package org.deri.rdf.browser.sparql;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.deri.rdf.browser.facet.RdfDecoratedValue;
import org.deri.rdf.browser.facet.RdfFacet;
import org.deri.rdf.browser.model.AnnotatedString;
import org.deri.rdf.browser.model.RdfResource;
import org.deri.rdf.browser.util.ParsingUtilities;

import com.google.common.collect.SetMultimap;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;

public class QueryEngine {
	
	public Model getResourcesRDF(String sparqlEndpoint,String filter, List<String> properties,SetMultimap<RdfFacet, RdfDecoratedValue> filters){
		int limit = 500;
		int offset = 0;
		Model totalModel = ModelFactory.createDefaultModel();
		Model model;
		do{
			//get the resources
			String sparql = "CONSTRUCT {?x ?p ?o } WHERE { ?x ?p ?o. ?x " + filter + getFilter("x",filter,filters) + " FILTER (isIRI(?x)). } ORDER BY ?x LIMIT " + limit + " OFFSET " + offset;
			model = execConstruct(sparql, sparqlEndpoint);
			totalModel.add(model);
			offset += limit;
			//throttle
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}while(! model.isEmpty());
		
		return totalModel;
	}

	public Collection<RdfResource> getResources(String sparqlEndpoint,String filter, List<String> properties,SetMultimap<RdfFacet, RdfDecoratedValue> filters, int offset, int limit){
		//get the resources
		//TODO support blank node
		//FIXME support blank node. currently, silently ignored
		String sparql = "SELECT DISTINCT ?x WHERE { ?x " + filter + getFilter("x",filter,filters) + " FILTER (isIRI(?x)). } ORDER BY ?x LIMIT " + limit + " OFFSET " + offset;
		ResultSet results = execSparql(sparql,sparqlEndpoint);
		Set<String> resources = new HashSet<String>(limit);
		Map<String,RdfResource> resourcesMap = new HashMap<String, RdfResource>();
		while(results.hasNext()){
			QuerySolution sol = results.next();
			//DOC limitation: resources can't be blank nodes
			String uri = sol.getResource("x").getURI();
			resources.add(uri);
			resourcesMap.put(uri, new RdfResource(uri));
		}
		
		if(resources.isEmpty()){
			return new HashSet<RdfResource>();
		}
		//get properties of resources
		//TODO make this configurable.... currently get *all* properties
		sparql = "SELECT ?x ?p ?o WHERE { ?x ?p ?o. ?x " + filter + getOrClause("x",resources) + getPropertiesFilter("p",properties) + " }";
		results = execSparql(sparql,sparqlEndpoint);
		while(results.hasNext()){
			QuerySolution sol = results.next();
			resourcesMap.get(sol.getResource("x").getURI()).getProperties().put(sol.getResource("p").getURI(),getString(sol.get("o")));
		}
		return resourcesMap.values();
	}
	
	public int getResourcesCount(String sparqlEndpoint, String filter, SetMultimap<RdfFacet, RdfDecoratedValue> filters){
		String sparql = "SELECT (COUNT(DISTINCT(?x)) AS ?count) WHERE { ?x " + filter + getFilter("x",filter,filters) + " FILTER (isIRI(?x)). } ";
		ResultSet results = execSparql(sparql, sparqlEndpoint);
		if(results.hasNext()){
			QuerySolution sol = results.next();
			return sol.getLiteral("count").getInt();
		}else{
			return 0;
		}
	}
	
	public Collection<AnnotatedString> getPropertiesWithCount(String sparqlEndpoint, String property, String filter, SetMultimap<RdfFacet, RdfDecoratedValue> filters){
		String sparql = "SELECT DISTINCT ?v (COUNT(DISTINCT(?x)) AS ?count) WHERE{ ?x " + property + " ?v. ?x " + filter + getFilter("x",filter,filters) + " } GROUP BY (?v)";
		ResultSet results = execSparql(sparql, sparqlEndpoint);
		Set<String> lblsTodo = new HashSet<String>();
		List<Set<String>> lblsTodoSets = new ArrayList<Set<String>>();
		Map<String, AnnotatedString> valuesMap = new HashMap<String, AnnotatedString>();
		while(results.hasNext()){
			QuerySolution sol = results.next();
			RDFNode node = sol.get("v");
			if(node==null){continue;}
			String id = getString(node);
			int type = node.canAs(Literal.class)?AnnotatedString.LITERAL:AnnotatedString.RESOURCE;
			if(id==null){
				continue;
			}
			valuesMap.put(id, new AnnotatedString(sol.getLiteral("count").getInt(),id,type));
			if(type==AnnotatedString.RESOURCE && !id.contains(" ")){
				//TODO hack some URIs in World Bank data contains space http://worldbank.270a.info/classification/country/Sub-Saharan Africa excluding South Africa
				//not sure whether this correct... suspect no
				//but Fuseki is not complaining upon importing... firing an error when queried
				lblsTodo.add(id);
				if(lblsTodo.size()>50){
					//divide into 50 subsets... more than that is too much for a single SPARQL query
					lblsTodoSets.add(lblsTodo);
					lblsTodo = new HashSet<String>();
				}
			}
		}
		if(lblsTodo.size()>0){
			lblsTodoSets.add(lblsTodo);
		}
		//get Labels if any todo 
		if(lblsTodoSets.size()>0){
			for(Set<String> set: lblsTodoSets){
				sparql = LABEL_SPARQL + getOrFilter(set,"item") + "}";
				results = execSparql(sparql, sparqlEndpoint);
				while(results.hasNext()){
					QuerySolution sol = results.next();
					String id = sol.getResource("item").getURI();
					String lbl = null;
					if(sol.get("lbl1")!=null){
						lbl = sol.getLiteral("lbl1").getString();
					}else if(sol.get("lbl2")!=null){
						lbl = sol.getLiteral("lbl2").getString();
					}
					if(lbl!=null){
						valuesMap.get(id).setLabel(lbl);
					}
				}
			}
		}
		
		Collection<AnnotatedString> values = new HashSet<AnnotatedString>(valuesMap.values());
		//now see if there are blank values
		sparql = "SELECT (COUNT(DISTINCT(?x)) AS ?count) WHERE{ ?x " + filter + "OPTIONAL{ ?x " + property + " ?v.}. FILTER(!bound(?v)). " + getFilter("x",filter,filters) + " }";
		results = execSparql(sparql, sparqlEndpoint);
		if(results.hasNext()){
			QuerySolution sol = results.next();
			int count = sol.getLiteral("count").getInt();
			if(count>0){
				values.add(new AnnotatedString(count, null,0));
			}
		}
		return values;
	}
	
	protected String getFilter(String varname, String mainSelector, SetMultimap<RdfFacet, RdfDecoratedValue> propertyFilters){
		if(propertyFilters.isEmpty()){
			return "";
		}
		StringBuilder builder = new StringBuilder("");
		int j = 1;
		for(Entry<RdfFacet, Collection<RdfDecoratedValue>> pv:propertyFilters.asMap().entrySet()){
			Collection<RdfDecoratedValue> values = pv.getValue();
			builder.append("{");
			//iterate through values
			int i = 1;
			Iterator<RdfDecoratedValue> valIter = values.iterator();
			String auxVarname = "?" + ParsingUtilities.varname(pv.getKey().getName()) + "_vvv";
			while(i<values.size()){
				RdfDecoratedValue val = valIter.next();
				if(val==null){
					//blank selected
					builder.append("{").append("?").append(varname).append(" ").append(mainSelector).append(" OPTIONAL{ ").append(pv.getKey().getResourceSparqlSelector(varname, new RdfDecoratedValue(auxVarname,false))).append(" . } FILTER(! bound(").append(auxVarname).append(") ) } UNION ");
				}else{
					
					//literals should be handled different than resources
					if(val.isLiteral()){
						builder.append("{ ").append(pv.getKey().getLiteralSparqlSelector(mainSelector,varname,varname+ i + "_"+ j+ "_lit",val)).append(" } UNION ");
					}else{
						builder.append("{ ").append(pv.getKey().getResourceSparqlSelector(varname,val)).append(" . } UNION ");
					}
				}
				i+=1;
			}
			//the last value
			RdfDecoratedValue val = valIter.next();
			if(val==null){
				//blank selected
				builder.append("{").append("?").append(varname).append(" ").append(mainSelector).append(" OPTIONAL{ ").append(pv.getKey().getResourceSparqlSelector(varname, new RdfDecoratedValue(auxVarname, false))).append(" . } FILTER(! bound(").append(auxVarname).append(") ) } ");
			}else{
				//literals should be handled different than resources
				if(val.isLiteral()){
					builder.append("{ ").append(pv.getKey().getLiteralSparqlSelector(mainSelector,varname,varname+ i + "_"+ j+ "_lit",val)).append(" } ");
				}else{
					builder.append("{ ").append(pv.getKey().getResourceSparqlSelector(varname,val)).append(" . } ");
				}
			}
			builder.append("}");
			j +=1;
		}
		return builder.toString();
	}
	
	protected String getOrClause(String varname, Set<String> resources) {
		if(resources.isEmpty()){
			return "";
		}
		StringBuilder builder = new StringBuilder("FILTER ( ");
		for(String r:resources){
			builder.append("?").append(varname).append(" = <").append(r).append("> || ");
		}
		//get rid of the last ||
		return builder.substring(0,builder.length()-4) + ").";
	}
	
	protected String getPropertiesFilter(String varname, List<String> properties) {
		if(properties.isEmpty()){
			return "";
		}
		StringBuilder builder = new StringBuilder("FILTER ( ");
		for(String p:properties){
			builder.append("?").append(varname).append(" = <").append(p).append("> || ");
		}
		//get rid of the last ||
		return builder.substring(0,builder.length()-4) + ").";
	}
	
	protected String getString(RDFNode node){
		if(node==null) return null;
		if(node.canAs(Literal.class)){
			return node.asLiteral().getString();
		}else{
			return node.asResource().getURI();
		}
	}
	
	protected String getVarnames(int numLabels){
		String vars = "";
		for(int i=0;i<numLabels;i++){
			vars += " ?o" + String.valueOf(i); 
		}
		return vars + "";
	}
	
	protected String getTriplePatterns(String varname, String[] labelProps){
		String patterns = "";
		String pref = "?" + varname + " <";
		for(int i=0;i<labelProps.length;i++){
			patterns += pref + labelProps[i] + "> ?o" + String.valueOf(i) + ". ";
		}
		return patterns;
	}
	
	private ResultSet execSparql(String sparql, String sparqlEndpointUrl) {
		//we use QueryEngineHTTP to skip query validation as Virtuoso needs non-standardised extensions and will not pass ARQ validation
		QueryEngineHTTP qExec = new QueryEngineHTTP(sparqlEndpointUrl, sparql);
		/*if(defaultGraphUri!=null){
			qExec.setDefaultGraphURIs(Collections.singletonList(defaultGraphUri));
		}*/
		ResultSet res = qExec.execSelect();
		return res;
	}
	
	private Model execConstruct(String sparql, String sparqlEndpointUrl) {
		QueryEngineHTTP qExec = new QueryEngineHTTP(sparqlEndpointUrl, sparql);
		return qExec.execConstruct();
	}
	
	private String getOrFilter(Collection<String> uris, String varname){
		if(uris.size()==0){
			return "";
		}
		StringBuilder sb = new StringBuilder();
		String itemF = "?" + varname + "=<";
		sb.append(" FILTER( ");
		for(String uri : uris){
			sb.append(itemF).append(uri).append("> || ");
		}
		sb.delete(sb.length()-3, sb.length());
		sb.append(")");
		return sb.toString();
	}
	private static final String LABEL_SPARQL = 
		"PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#> " +
		"PREFIX skos:<http://www.w3.org/2004/02/skos/core#> " +
		"SELECT ?item ?lbl1 ?lbl2 " +
		"WHERE{" +
		" OPTIONAL {?item skos:prefLabel ?lbl1. } " +
		" OPTIONAL {?item rdfs:label ?lb2 .}" ; 
}
