// Generated by CoffeeScript 1.6.3
(function() {
  var __hasProp = {}.hasOwnProperty,
    __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor(); child.__super__ = parent.prototype; return child; };

  define(['backbone.marionette', 'handlebars', 'select-list'], function(Marionette, Handlebars) {
    var QualityGateDetailProjectsView, _ref;
    return QualityGateDetailProjectsView = (function(_super) {
      __extends(QualityGateDetailProjectsView, _super);

      function QualityGateDetailProjectsView() {
        _ref = QualityGateDetailProjectsView.__super__.constructor.apply(this, arguments);
        return _ref;
      }

      QualityGateDetailProjectsView.prototype.template = Handlebars.compile(jQuery('#quality-gate-detail-projects-template').html());

      QualityGateDetailProjectsView.prototype.onRender = function() {
        if (!this.model.get('default')) {
          return new SelectList({
            el: this.$('#select-list-projects'),
            width: '100%',
            readOnly: !this.options.app.canEdit,
            format: function(item) {
              return item.name;
            },
            searchUrl: "" + baseUrl + "/api/qualitygates/search?gateId=" + this.options.gateId,
            selectUrl: "" + baseUrl + "/api/qualitygates/select",
            deselectUrl: "" + baseUrl + "/api/qualitygates/deselect",
            extra: {
              gateId: this.options.gateId
            },
            selectParameter: 'projectId',
            selectParameterValue: 'id',
            labels: {
              selected: window.SS.phrases.projects["with"],
              deselected: window.SS.phrases.projects.without,
              all: window.SS.phrases.projects.all,
              noResults: window.SS.phrases.projects.noResults
            },
            tooltips: {
              select: window.SS.phrases.projects.select_hint,
              deselect: window.SS.phrases.projects.deselect_hint
            }
          });
        }
      };

      return QualityGateDetailProjectsView;

    })(Marionette.ItemView);
  });

}).call(this);
