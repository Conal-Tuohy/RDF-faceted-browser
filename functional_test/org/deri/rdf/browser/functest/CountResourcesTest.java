package org.deri.rdf.browser.functest;

import java.util.HashSet;
import java.util.Set;

import org.deri.rdf.browser.RdfEngine;
import org.deri.rdf.browser.model.Facet;
import org.deri.rdf.browser.model.FacetFilter;
import org.deri.rdf.browser.model.MainFilter;
import org.deri.rdf.browser.sparql.SparqlEngine;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class CountResourcesTest {

	RdfEngine rdfEngine;
	SparqlEngine sparqlEngine;
	String endpoint = "http://localhost:3030/test/query";
	MainFilter mainFilter = new MainFilter("s", "s", "a <http://xmlns.com/foaf/0.1/Person> .");
	
	@BeforeClass
	public void init(){
		sparqlEngine = new SparqlEngine();
		rdfEngine = new RdfEngine();
	}
	
	@Test
	public void noFilter(){
		Set<Facet> facets = new HashSet<Facet>();
		String sparql = sparqlEngine.countFocusItemsSparql(mainFilter, facets);
		long count = rdfEngine.getResourcesCount(sparql, endpoint);
		assertEquals(count,4);
	}
	
	@Test
	public void oneFacetOneValue(){
		Set<Facet> facets = new HashSet<Facet>();
		Facet memberFacet = new Facet(new FacetFilter("<http://xmlns.com/foaf/0.1/member>"), "org", "organisation");
		memberFacet.addResourceValue("http://example.org/organisation/deri");
		facets.add(memberFacet);
		String sparql = sparqlEngine.countFocusItemsSparql(mainFilter, facets);
		long count = rdfEngine.getResourcesCount(sparql, endpoint);
		assertEquals(count,2);
	}
	
	@Test
	public void oneFacetTwoValue(){
		Set<Facet> facets = new HashSet<Facet>();
		Facet memberFacet = new Facet(new FacetFilter("<http://xmlns.com/foaf/0.1/member>"), "org", "organisation");
		memberFacet.addResourceValue("http://example.org/organisation/deri");
		memberFacet.addResourceValue("http://example.org/organisation/w3c");
		facets.add(memberFacet);
		String sparql = sparqlEngine.countFocusItemsSparql(mainFilter, facets);
		long count = rdfEngine.getResourcesCount(sparql, endpoint);
		assertEquals(count,4);
	}
	
	@Test
	public void twoFacets(){
		Set<Facet> facets = new HashSet<Facet>();
		Facet memberFacet = new Facet(new FacetFilter("<http://xmlns.com/foaf/0.1/member>"), "org", "organisation");
		memberFacet.addResourceValue("http://example.org/organisation/deri");
		facets.add(memberFacet);
		Facet hobbyFacet = new Facet(new FacetFilter("<http://example.org/property/hobby>"), "hobby", "hobby");
		hobbyFacet.addLiteralValue("football");
		facets.add(hobbyFacet);
		String sparql = sparqlEngine.countFocusItemsSparql(mainFilter, facets);
		long count = rdfEngine.getResourcesCount(sparql, endpoint);
		assertEquals(count,1);
	}
	
	@Test
	public void twoFacetsMultipleValues(){
		Set<Facet> facets = new HashSet<Facet>();
		Facet memberFacet = new Facet(new FacetFilter("<http://xmlns.com/foaf/0.1/member>"), "org", "organisation");
		memberFacet.addResourceValue("http://example.org/organisation/deri");
		facets.add(memberFacet);
		Facet hobbyFacet = new Facet(new FacetFilter("<http://example.org/property/hobby>"), "hobby", "hobby");
		hobbyFacet.addLiteralValue("football");
		hobbyFacet.addLiteralValue("rugby");
		facets.add(hobbyFacet);
		String sparql = sparqlEngine.countFocusItemsSparql(mainFilter, facets);
		long count = rdfEngine.getResourcesCount(sparql, endpoint);
		assertEquals(count,2);
	}
	
	@Test
	public void twoFacetsNoResults(){
		Set<Facet> facets = new HashSet<Facet>();
		Facet memberFacet = new Facet(new FacetFilter("<http://xmlns.com/foaf/0.1/member>"), "org", "organisation");
		memberFacet.addResourceValue("http://example.org/organisation/deri");
		facets.add(memberFacet);
		Facet hobbyFacet = new Facet(new FacetFilter("<http://example.org/property/hobby>"), "hobby", "hobby");
		hobbyFacet.addLiteralValue("piano");
		facets.add(hobbyFacet);
		String sparql = sparqlEngine.countFocusItemsSparql(mainFilter, facets);
		long count = rdfEngine.getResourcesCount(sparql, endpoint);
		assertEquals(count,0);
	}
}
