/*

Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

 * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
 * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

function RdfRangeFacet(div, config, options) {
  this._div = div;
  this._config = config;
  this._options = options;

  this._from = ("from" in this._config) ? this._config.from : null;
  this._to = ("to" in this._config) ? this._config.to : null;

  this._selectNumeric = ("selectNumeric" in this._config) ? this._config.selectNumeric : true;
  this._selectNonNumeric = ("selectNonNumeric" in this._config) ? this._config.selectNonNumeric : true;
  this._selectBlank = ("selectBlank" in this._config) ? this._config.selectBlank : true;
  this._selectError = ("selectError" in this._config) ? this._config.selectError : true;

  this._baseNumericCount = 0;
  this._baseNonNumericCount = 0;
  this._baseBlankCount = 0;
  this._baseErrorCount = 0;

  this._numericCount = 0;
  this._nonNumericCount = 0;
  this._blankCount = 0;
  this._errorCount = 0;

  this._error = false;
  this._initializeUI();
  this._initializedUI = true;
  
}

RdfRangeFacet.prototype.reset = function() {
  this._from = this._config.min;
  this._to = this._config.max;
  this._selected = false;
  this._sliderWidget.update(
      this._config.min, 
      this._config.max, 
      this._config.step, 
      this._from,
      this._to
  );

  this._selectNumeric = true;
  this._selectNonNumeric = true;
  this._selectBlank = true;
  this._selectError = true;

  this._setRangeIndicators();
};

RdfRangeFacet.reconstruct = function(div, uiState) {
  return new RdfRangeFacet(div, uiState.c, uiState.o);
};

RdfRangeFacet.prototype.dispose = function() {
};

RdfRangeFacet.prototype.getUIState = function() {
  var json = {
      c: this.getJSON(),
      o: this._options
  };

  return json;
};

RdfRangeFacet.prototype.getJSON = function() {
  var o = {
      type: "rdf-range",
      name: this._config.name,
      replaceCommas: this._config.replace_commas,
      expression: this._config.expression,
      property:this._config.property,
      selectNumeric: this._selectNumeric,
      selectNonNumeric: this._selectNonNumeric,
      selectBlank: this._selectBlank,
      selectError: this._selectError
  };

  if (this._from !== null) {
    o.from = this._from;
  }
  if (this._to !== null) {
    o.to = this._to;
  }

  return o;
};

RdfRangeFacet.prototype.hasSelection = function() {
  if (!this._selectNumeric || !this._selectNonNumeric || !this._selectBlank || !this._selectError) {
    return true;
  }

  return (this._from !== null && (!this._initializedUI || this._from > this._config.min)) ||
  (this._to !== null && (!this._initializedUI || this._to < this._config.max));
};

RdfRangeFacet.prototype._initializeUI = function() {
  var self = this;
  this._div
  .empty()
  .show()
  .html(
      '<div class="facet-title" bind="headerDiv">' +
      '<div class="grid-layout layout-tightest layout-full"><table><tr>' +
      '<td width="1%"><a href="javascript:{}" title="Remove this facet" class="facet-title-remove" bind="removeButton">&nbsp;</a></td>' +
      '<td>' +
      '<a href="javascript:{}" class="facet-choice-link" bind="resetButton">reset</a>' +
      '<a href="javascript:{}" class="facet-choice-link" bind="changeButton">change</a>' +
      '<span bind="facetTitle"></span>' +
      '</td>' +
      '</tr></table></div>' +
      '</div>' +
      '<div class="facet-expression" bind="expressionDiv" title="Click to edit expression"></div>' +
      '<div class="facet-range-body">' +
      '<div class="facet-range-message" bind="messageDiv">Loading...</div>' +
      '<div class="facet-range-slider" bind="sliderWidgetDiv">' +
      '<div class="facet-range-histogram" bind="histogramDiv"></div>' +
      '</div>' +
      '<div class="facet-range-status" bind="statusDiv"></div>' +
      '<div class="facet-range-other-choices" bind="otherChoicesDiv"></div>' +
      '</div>'
  );
  this._elmts = DOM.bind(this._div);

  this._elmts.facetTitle.text(this._config.name);
  this._elmts.changeButton.attr("title","Current Expression: " + this._config.expression).click(function() {
    self._elmts.expressionDiv.slideToggle(100, function() {
      if (self._elmts.expressionDiv.css("display") != "none") {
        //self._editExpression();
      }
    });
  });
  this._elmts.expressionDiv.text(this._config.expression).click(function() { 
    self._editExpression(); 
  }).hide();

  this._elmts.resetButton.click(function() {
    self.reset();
    self._updateRest();
  });
  this._elmts.removeButton.click(function() {
    self._remove();
  });

  this._histogram = new HistogramWidget(this._elmts.histogramDiv, { binColors: [ "#bbccff", "#88aaee" ] });
  this._sliderWidget = new SliderWidget(this._elmts.sliderWidgetDiv);

  this._elmts.sliderWidgetDiv.bind("slide", function(evt, data) {
    self._from = data.from;
    self._to = data.to;
    self._setRangeIndicators();
  }).bind("stop", function(evt, data) {
    self._from = data.from;
    self._to = data.to;
    self._selectNumeric = true;
    self._updateRest();
  });
};

RdfRangeFacet.prototype._renderOtherChoices = function() {
  var self = this;
  var container = this._elmts.otherChoicesDiv.empty();

  if (this._baseNonNumericCount === 0 && this._baseBlankCount === 0 && this._baseErrorCount === 0) {
    return;
  }

  var facet_id = this._div.attr("id");

  var choices = $('<div>').addClass("facet-range-choices");

  // ----------------- numeric -----------------

  var numericDiv = $('<div class="facet-range-item"></div>').appendTo(choices);

  var numericLabel = $('<label>').attr("for", facet_id + "-numeric").appendTo(numericDiv);    
  $('<span>').text("Numeric ").addClass("facet-range-choice-label").appendTo(numericLabel);
  $('<div>').text(this._numericCount).addClass("facet-range-choice-count").appendTo(numericLabel);

  // ----------------- non-numeric -----------------

  var nonNumericDiv = $('<div class="facet-range-item"></div>').appendTo(choices);    

  var nonNumericLabel = $('<label>').attr("for", facet_id + "-non-numeric").appendTo(nonNumericDiv);    
  $('<span>').text("Non-numeric ").addClass("facet-range-choice-label").appendTo(nonNumericLabel);
  $('<div>').text(this._nonNumericCount).addClass("facet-range-choice-count").appendTo(nonNumericLabel);

  // ----------------- blank -----------------

  var blankDiv = $('<div class="facet-range-item"></div>').appendTo(choices);        

  var blankLabel = $('<label>').attr("for", facet_id + "-blank").appendTo(blankDiv);    
  $('<span>').text("Blank ").addClass("facet-range-choice-label").appendTo(blankLabel);
  $('<div>').text(this._blankCount).addClass("facet-range-choice-count").appendTo(blankLabel);

  // --------------------------

  choices.appendTo(container);
};

RdfRangeFacet.prototype._setRangeIndicators = function() {
  this._elmts.statusDiv.html(this._addCommas(this._from.toFixed(2)) + " &mdash; " + this._addCommas(this._to.toFixed(2)));
};

RdfRangeFacet.prototype._addCommas = function(nStr) {
  nStr += '';
  x = nStr.split('.');
  x1 = x[0];
  x2 = x.length > 1 ? '.' + x[1] : '';
  var rgx = /(\d+)(\d{3})/;
  while (rgx.test(x1)) {
    x1 = x1.replace(rgx, '$1' + ',' + '$2');
  }
  return x1 + x2;
};

RdfRangeFacet.prototype.updateState = function(data) {
  if ("min" in data && "max" in data) {
    this._error = false;

    this._config.min = data.min;
    this._config.max = data.max;
    this._config.step = data.step;
    this._baseBins = data.baseBins;
    this._bins = data.bins;

    switch (this._config.mode) {
    case "min":
      this._from = Math.max(data.from, this._config.min);
      break;
    case "max":
      this._to = Math.min(data.to, this._config.max);
      break;
    default:
      this._from = Math.max(data.from, this._config.min);
    if ("to" in data) {
      this._to = Math.min(data.to, this._config.max);
    } else {
      this._to = data.max;
    }
    }

    this._baseNumericCount = data.baseNumericCount;
    this._baseNonNumericCount = data.baseNonNumericCount;
    this._baseBlankCount = data.baseBlankCount;
    this._baseErrorCount = data.baseErrorCount;

    this._numericCount = data.numericCount;
    this._nonNumericCount = data.nonNumericCount;
    this._blankCount = data.blankCount;
    this._errorCount = data.errorCount;
  } else {
    this._error = true;
    this._errorMessage = "error" in data ? data.error : "Unknown error.";
  }

  this.render();
};

RdfRangeFacet.prototype.render = function() {
  if (!this._initializedUI) {
    this._initializeUI();
    this._initializedUI = true;
  }

  if (this._error) {
    this._elmts.messageDiv.text(this._errorMessage).show();
    this._elmts.sliderWidgetDiv.hide();
    this._elmts.histogramDiv.hide();
    this._elmts.statusDiv.hide();
    this._elmts.otherChoicesDiv.hide();
    return;
  }

  this._elmts.messageDiv.hide();
  this._elmts.sliderWidgetDiv.show();
  this._elmts.histogramDiv.show();
  this._elmts.statusDiv.show();
  this._elmts.otherChoicesDiv.show();

  this._sliderWidget.update(
      this._config.min, 
      this._config.max, 
      this._config.step, 
      this._from,
      this._to
  );
  this._histogram.update(
      this._config.min, 
      this._config.max, 
      this._config.step, 
      [ this._baseBins, this._bins ]
  );

  this._setRangeIndicators();
  this._renderOtherChoices();
};

RdfRangeFacet.prototype._remove = function() {
  ui.browsingEngine.removeFacet(this);

  this._div = null;
  this._config = null;
  this._data = null;
};

RdfRangeFacet.prototype._updateRest = function() {
	RdfBrowser.update({ engineChanged: true });
};


RdfRangeFacet.prototype.setLoadingState = function(){
	this._elmts.sliderWidgetDiv.hide();
    this._elmts.histogramDiv.hide();
    this._elmts.statusDiv.hide();
    this._elmts.otherChoicesDiv.hide();
	this._elmts.messageDiv.text('loading...').show();
};