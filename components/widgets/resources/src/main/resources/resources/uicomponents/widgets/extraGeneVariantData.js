var ExtraGeneVariantData = (function (ExtraGeneVariantData) {
  var tools = ExtraGeneVariantData.tools = ExtraGeneVariantData.tools || {};
  tools.Editor = Class.create({

    initialize : function (geneTable, options) {
      var localOpts = options || {};
      this.options = localOpts.global || {'geneIndex' : 0, 'variantIndex' : 0};
      if (geneTable) {
        this._geneTable = geneTable;
        this.geneClassName = localOpts.geneClassName || 'PhenoTips.GeneClass';
        this.geneVariantClassName = localOpts.geneVariantClassName || 'PhenoTips.GeneVariantClass';
        this._hasVariants = geneTable.hasClassName('hasVariants');

        //if edit mode
        if ($('inline')) {
          // getting rid of 'for' attributes in labels and 'id' in inputs
          this._geneTable.select('label.xwiki-form-listclass').each(function (label) {
            label.removeAttribute("for");
            label.down('input').removeAttribute("id");
          });

          this.warnSaving = false;
          this.areaEditDataListenerArray = [];
          this.getNewRowsTemplates();
          this.restrictNumericInput();

          this._geneTable.select('.gene a.delete-gene').invoke('observe', 'click', this.ajaxDeleteGeneData.bindAsEventListener(this));
          $$('a.button.add-gene.add-data-button').invoke('observe', 'click', this.ajaxAddGeneData.bindAsEventListener(this));

          if (this._hasVariants) {
            this.createEditButtons('.variant-moreinfo-editbutton-row');
            this.createEditDoneButtons('.variant-moreinfo-editdonebutton-row');
            this._geneTable.select('a.variant-edit').invoke('observe', 'click', this.editData.bindAsEventListener(this));
            this._geneTable.select('a.variant-edit-done').invoke('observe', 'click', this.editDoneData.bindAsEventListener(this));
            this._geneTable.select('.variant.moreinfo').invoke('observe', 'click', this.areaEditData.bindAsEventListener(this));
            this._geneTable.select('.variant a.delete-variant').invoke('observe', 'click', this.ajaxDeleteVariantData.bindAsEventListener(this));
            this._geneTable.select('a.button.add-variant.add-data-button').invoke('observe', 'click', this.ajaxAddVariantData.bindAsEventListener(this));
            //invoking click event to hide rows with empty inputs in 'more info' section
            this._geneTable.select('a.variant-edit-done').invoke('click');
          }

          this.lockGeneInput();
        }

        this._geneTable.select('tr tr').each( function(item) {
          item.toggleClassName('moreinfo-view', true);
        });
        if (this._hasVariants) {
          this.createShowMoreinfoButtons('td.variant-row-count.variant');
          this.createHideShowButtons('.variant.variant-title');
        }
        this._geneTable.select('tr th')[0].width = '1em';
      }
    },

    areaEditData  : function (event) {
      var el = null;
      if (event.element().className == 'variant moreinfo')
        el = event.element();
      else
        el = event.element().up('td.variant.moreinfo');
      el.select('a.variant-edit').invoke('click');
      var className = el.down('.variant-moreinfo-editbutton-row').className;
      var varIndex = className.substring(className.lastIndexOf('-') + 1);
      event.stopPropagation();
      if ( this.areaEditDataListenerArray.indexOf(varIndex) < 0 ) {
        this.areaEditDataListenerArray.push(varIndex);
        var _this = this;
        document.observe('click', function (event) {
          if (!event.element().up('.variant-' + varIndex)){
            _this._geneTable.select('.variant-' + varIndex + ' a.variant-edit-done').invoke('click');
          }
        });
      }
    },

    getNewRowsTemplates : function () {
      var sizep = this._geneTable.select('.variant-gene-ZZGENE_INDEX_PLACEHOLDERZZ').size();
      var geneRowTemplateEl = this._geneTable.select('.variant-gene-ZZGENE_INDEX_PLACEHOLDERZZ')[0].previous();
      var buttonRowTemplateEl = this._geneTable.select('.variant-gene-ZZGENE_INDEX_PLACEHOLDERZZ')[sizep - 5];
      if (this._hasVariants) {
        var varHeader_0_TemplateEl = this._geneTable.select('.variant-gene-ZZGENE_INDEX_PLACEHOLDERZZ')[sizep - 4];
        var varHeader_1_TemplateEl = this._geneTable.select('.variant-gene-ZZGENE_INDEX_PLACEHOLDERZZ')[sizep - 3];
        var varRowTemplateEl = this._geneTable.select('.variant-gene-ZZGENE_INDEX_PLACEHOLDERZZ')[sizep - 2];
        var varMoreInfoTemplateEl = this._geneTable.select('.variant-gene-ZZGENE_INDEX_PLACEHOLDERZZ')[sizep - 1];
      }
      this._geneTable.select('.variant-gene-ZZGENE_INDEX_PLACEHOLDERZZ')[0].previous().remove();
      this._geneTable.select('.variant-gene-ZZGENE_INDEX_PLACEHOLDERZZ').each(function(item) {
        item.remove();
      });
      //making templates
      var syntax = /(^|.|\r|\n)(ZZ(\w+)ZZ)/;
      this.geneRowTemplate = new Template(geneRowTemplateEl.innerHTML, syntax);
      if (this._hasVariants) {
        this.addVariantButtonTemplate = new Template(buttonRowTemplateEl.innerHTML, syntax);
        this.variantHeaderTemplate_0  = new Template(varHeader_0_TemplateEl.innerHTML, syntax);
        this.variantHeaderTemplate_1  = new Template(varHeader_1_TemplateEl.innerHTML, syntax);
        this.variantRowTemplate  = new Template(varRowTemplateEl.innerHTML, syntax);
        this.moreInfoSectionTemplate = new Template(varMoreInfoTemplateEl.innerHTML, syntax);
      }
    },

    ajaxDeleteGeneData : function (event) {
      event.stop();

      var deleteTrigger = event.element();
      var className = deleteTrigger.up('td').className;
      var geneIndex = parseInt(className.substring(className.lastIndexOf('-') + 1), 10);
      var geneId = this._geneTable.select('[name="' + this.geneClassName + '_' + geneIndex + '_gene"]')[0].value;

      var _this = this;
      new XWiki.widgets.ConfirmedAjaxRequest(deleteTrigger.href + geneId, {

        onCreate : function() {
          deleteTrigger.disabled = true;
        },

        onSuccess : function() {
          var dataRow = deleteTrigger.up('tr:not(.head-group)');
          if (dataRow) {
            var geneIdInput = dataRow.down('input.gene-id');
            geneIdInput.remove();
            var geneNameInput = dataRow.down('input.gene-name');
            geneNameInput.__validation.destroy();
            _this._geneTable.select('.variant-gene-' + geneIndex).each(function(item) {
              item.remove();
            });
            dataRow.remove();
          }
          if (_this._geneTable) {
            var i = 1;
            _this._geneTable.select('td.row-count').each(function(item) {
              var geneRowIndex = item.next().className.substring(item.next().className.lastIndexOf('-') + 1);
              var y = 1;
              _this._geneTable.select('.variant-hide-heading-' + geneRowIndex + ' .variant-row-count').each(function(vitem) {
                vitem.childNodes[1].textContent = i + '.' + (y++);
              });
              item.update(i++);
            });
          }
        },

        onComplete : function() {
          deleteTrigger.disabled = false;
        }

      },
      {
         confirmationText : "$!services.localization.render('phenotips.tableMacros.rowDeleteConfirmation')"
      });
    },

    ajaxAddGeneData : function (event) {
      event.stop();

      var addTrigger = event.element();
      var listActions = addTrigger.up('div.list-actions');
      var targetTable = listActions && listActions.previous('table.gene-table');
      if (this._geneTable !== targetTable) { return; }
      if (addTrigger.disabled) {
        return;
      }

      if (!this._geneTable) {
        new XWiki.widgets.Notification("$services.localization.render('phenotips.tableMacros.listNotFound')", 'error');
      }

      var idx = this._geneTable.select('td.row-count').size() + 1;
      var geneIndex = this.options.geneIndex++;

      var newGeneRow = new Element('tr');
      var data = {GENE_NUMBER_PLACEHOLDER: idx, GENE_INDEX_PLACEHOLDER: geneIndex};
      var geneRowInner = this.geneRowTemplate.evaluate(data);

      geneRowInner = geneRowInner.replace(/_\d+_|=\d+&/g, function (placeholder) {
        switch(true) {
          case /_\d+_/.test(placeholder):
            return '_' + geneIndex + '_';
          case /=\d+&/.test(placeholder):
            return '=' + geneIndex + '&';
          default:
            return "";
        }
      });

      newGeneRow.insert(geneRowInner);
      this._geneTable.down('tbody').insert(newGeneRow);

      if (this._hasVariants) {
          var newButtonRow = new Element('tr', {class :'variant-gene-' + geneIndex + ' variant-footer v-collapsed'});
          var buttonRowInner = this.addVariantButtonTemplate.evaluate(data);
          newButtonRow.insert(buttonRowInner);
          this._geneTable.down('tbody').insert(newButtonRow);
          var hintTool = newButtonRow.down('span.fa.fa-question-circle.xHelpButton');
          var icon_helpController = new PhenoTips.widgets.HelpButton(hintTool);
      }

      newGeneRow.down('a.delete-gene').observe('click', this.ajaxDeleteGeneData.bindAsEventListener(this));
      this._hasVariants && this._geneTable.select('tr.variant-gene-' + geneIndex + ' a.add-variant')[0].observe('click', this.ajaxAddVariantData.bindAsEventListener(this));

      $(this.geneClassName + '_' + geneIndex + '_status').value = "candidate";

      var geneLabel = new Element('p', {class :' gene col-label gene-' + geneIndex + ' gene-input-label v-collapsed'});
      newGeneRow.down('.suggested.suggest-gene.gene-name').insert({before: geneLabel});

      Event.fire(document, 'xwiki:dom:updated', {elements :[newGeneRow]});
    },

    ajaxDeleteVariantData : function (event) {
      event.stop();
      var deleteTrigger = event.element();
      var geneClass = this.geneClassName;
      if (deleteTrigger.disabled) {
         return;
      }
      var _this = this;
      new XWiki.widgets.ConfirmedAjaxRequest(deleteTrigger.href, {

        onCreate : function() {
          deleteTrigger.disabled = true;
        },

        onSuccess : function() {
          var dataRow = deleteTrigger.up('tr:not(.head-group)');
          if (dataRow) {
            var cdnaInput = dataRow.down('.variant.cdna input');
            cdnaInput.__validation.destroy();
            var className = dataRow.previous().className;
            var geneIndex = parseInt(className.substring(className.lastIndexOf('-') + 1), 10);
            //we are deleting the only variant in the gene: remove variant table header,
            //  unlock the genesymbol input field
            if (dataRow.previous().hasClassName('variant-title-row') && dataRow.next(1).hasClassName('variant-footer')) {
              _this._geneTable.select('.variant-gene-' + geneIndex + '.variant-title-row').each(function(item) {
                item.remove();
              });
              _this._geneTable.select('[name="' + geneClass + '_' + geneIndex + '_symbol"]')[0].toggleClassName('v-collapsed', false);
              _this._geneTable.select('.gene.col-label.gene-' + geneIndex + '.gene-input-label')[0].toggleClassName('v-collapsed', true);
              dataRow.next().remove();
              dataRow.remove();
            } else {
              //we are removing variant from the middle of the table-> updating table count
              dataRow.next().remove();
              dataRow.remove();
              var element = deleteTrigger.up('tr').select('.variant-row-count.variant')[0];
              // Get inner text in cross-browser manner
              var innerText = element.textContent || element.innerText;
              var geneCount = innerText.substring(0, innerText.indexOf('.'));
              var i = 1;
              _this._geneTable.select('.variant-hide-heading-' + geneIndex + ' .variant-row-count').each(function(item) {
                item.childNodes[1].textContent = geneCount + '.' + (i++);
              });
              //-update variant table header count
              var header = _this._geneTable.select('.variant.variant-title.gene-' + geneIndex)[0].firstChild;
              var newHeaderText = header.textContent.replace(/(\().+?(\))/g, "$1" + (--i) + "$2");
              header.textContent = newHeaderText;
            }
          }
        },

        onComplete : function() {
          deleteTrigger.disabled = false;
        }

      },
      {
         confirmationText : "$!services.localization.render('phenotips.tableMacros.rowDeleteConfirmation')"
      });
    },

    ajaxAddVariantData : function (event) {
      event.stop();

      var addTrigger = event.element();
      if (addTrigger.disabled) {
        return;
      }

      var xWikiClassNameMatches = /classname=([^&]+)/.exec(addTrigger.href);
      if (xWikiClassNameMatches == null) {
        new XWiki.widgets.Notification("$services.localization.render('phenotips.tableMacros.typeNotFound')", 'error');
        return;
      } else {
        var xWikiClassName = xWikiClassNameMatches[1];
      }

      var className = addTrigger.up('td.variant').className;
      var geneIndex = className.substring(className.lastIndexOf('-') + 1);
      var geneId = this._geneTable.select('[name="' + this.geneClassName + '_' + geneIndex + '_gene"]')[0].value;
      var geneSymbol = this._geneTable.select('.gene.col-label.gene-' + geneIndex)[0].innerHTML;

      //lock the genesymbol input field
      this._geneTable.select('[name="' + this.geneClassName + '_' + geneIndex + '_symbol"]')[0].toggleClassName('v-collapsed', true);
      this._geneTable.select('.gene.col-label.gene-' + geneIndex + '.gene-input-label')[0].toggleClassName('v-collapsed', false);

      var variantFooter = addTrigger.up('tr.variant-footer');
      if (!variantFooter) {
        new XWiki.widgets.Notification("$services.localization.render('phenotips.tableMacros.listNotFound') " + xWikiClassName, 'error');
      }

      var varCount = 1;
      var geneCount = parseInt(this._geneTable.select('td.gene.gene-' + geneIndex)[0].up('tr').down('td').innerHTML, 10);
      var varIndex = this.options.variantIndex++;

      var data = {
          GENE_NAME_PLACEHOLDER: geneSymbol,
          VARIANT_COUNT_PLACEHOLDER: varCount,
          GENE_INDEX_PLACEHOLDER: geneIndex,
          VARIANT_INDEX_PLACEHOLDER: varIndex
        };

      //If we insert first row of variants - create headers
      if (variantFooter.previous().className === '') {
        var varHeader_0_Inner = this.variantHeaderTemplate_0.evaluate(data);
        var newVariantHeader_0 = new Element('tr', {class :'variant-gene-' + geneIndex + ' variant-title-row'});
        newVariantHeader_0.update(varHeader_0_Inner);
        variantFooter.insert({before : newVariantHeader_0});

        var varHeader_1_Inner = this.variantHeaderTemplate_1.evaluate(data);
        var newVariantHeader_1 = new Element('tr', {class :'variant-gene-' + geneIndex + ' variant-title-row  variant-hide-heading-' + geneIndex});
        newVariantHeader_1.update(varHeader_1_Inner);
        variantFooter.insert({before : newVariantHeader_1});

        this.createHideShowButtons('.variant.variant-title.gene-' + geneIndex);
      } else {
        //update variant table header count
        varCount = this._geneTable.select('.variant-hide-heading-' + geneIndex + ' .variant-row-count').size() + 1;
        var header = this._geneTable.select('.variant.variant-title.gene-' + geneIndex)[0].firstChild;
        var newHeaderText = header.textContent.replace(/(\().+?(\))/g, "$1" + varCount + "$2");
        header.textContent = newHeaderText;
        data.VARIANT_COUNT_PLACEHOLDER = varCount;
      }

      data.VRCOUNT_PLACEHOLDER = geneCount + '.' + varCount;

      var _this = this;
      var evaluateTemplate = function evaluateTemplate(placeholder) {
        switch(true) {
          case /_\d+_/.test(placeholder):
            return '_' + varIndex + '_';
          case /=\d+&/.test(placeholder):
            return '=' + varIndex + '&';
          case /\$\{object.xWikiClass.name\}/.test(placeholder):
            return _this.geneVariantClassName;
          case /\$\{object.number\}/.test(placeholder):
            return varIndex;
          default:
            return "";
        }
      };

      var newVariantRow = new Element('tr', {class :'variant-gene-' + geneIndex + ' variant-hide-heading-' + geneIndex});
      var varRowInner = this.variantRowTemplate.evaluate(data);
      varRowInner = varRowInner.replace(/_\d+_|=\d+&|\$\{object.xWikiClass.name\}|\$\{object.number\}/g,
        function (placeholder) {
          return evaluateTemplate(placeholder);
        });
      newVariantRow.insert(varRowInner);
      variantFooter.insert({before : newVariantRow});

      var newMoreInfoRow = new Element('tr', {class :'variant-gene-' + geneIndex + ' variant-moreinfo-row variant-hide-heading-' + geneIndex});
      var varMoreInfoInner = this.moreInfoSectionTemplate.evaluate(data);
      varMoreInfoInner = varMoreInfoInner.replace(/_\d+_|=\d+&|\$\{object.xWikiClass.name\}|\$\{object.number\}/g,
        function (placeholder) {
          return evaluateTemplate(placeholder);
        });
      newMoreInfoRow.insert(varMoreInfoInner);
      variantFooter.insert({before : newMoreInfoRow});

      this.restrictNumericInput(newMoreInfoRow);

      // get the last value of reference_genome of all variants to create a new variant row with this value
      refGenomes = this._geneTable.select('select[id$="_reference_genome"]');
      if (refGenomes.length > 0) {
        newMoreInfoRow.down('select[id$="_reference_genome"]').value = refGenomes[refGenomes.length -1].value;
      }

      newVariantRow.down('a.delete-variant').observe('click', this.ajaxDeleteVariantData.bindAsEventListener(this));

      this.createEditButtons('.variant-moreinfo-editbutton-row.variant-' + varIndex);
      this.createEditDoneButtons('.variant-moreinfo-editdonebutton-row.variant-' + varIndex);

      newMoreInfoRow.select('a.variant-edit').invoke('observe', 'click', this.editData.bindAsEventListener(this));
      newMoreInfoRow.select('a.variant-edit-done').invoke('observe', 'click', this.editDoneData.bindAsEventListener(this));
      //invoking click event to show rows with empty inputs in 'more info' section
      newMoreInfoRow.select('a.variant-edit').invoke('click');

      this.createShowMoreinfoButtons('td.variant-row-count.variant-' + varIndex, true);

      newMoreInfoRow.select('.variant-moreinfo-editbutton-row tr').each( function(item) {
        item.toggleClassName('moreinfo-view', true);
      });
      newMoreInfoRow.select('.variant.moreinfo').invoke('observe', 'click', this.areaEditData.bindAsEventListener(this));
      newMoreInfoRow.select('.variant.moreinfo').invoke('click');

      $(this.geneVariantClassName + '_' + varIndex + '_gene').value = geneId;

      Event.fire(document, 'xwiki:dom:updated', {elements :[newVariantRow]});
    },

    createHideShowButtons : function (className) {
      //creating hide/Show variant section buttons
      var _this = this;
      this._geneTable.select(className).each( function(item){
        var geneIndex = item.className.substring(item.className.lastIndexOf('-') + 1);
        var showIcon = new Element('span', {class :'fa fa-plus-square-o fa-lg'});
        var variantShow = new Element('button', {class :'tool button secondary v-collapsed', type :'button'})
          .insert(showIcon)
          .insert(" $services.localization.render('PhenoTips.GeneVariantClass.expand')");
        var hideIcon = new Element('span', {class :'fa fa-minus-square-o fa-lg'});
        var variantHide = new Element('button', {class :'tool button secondary', type :'button'})
          .insert(hideIcon)
          .insert(" $services.localization.render('PhenoTips.GeneVariantClass.collapse')");
        var variantShowWrapper = new Element('span', {class :'buttonwrapper'}).insert(variantShow);
        var variantHideWrapper = new Element('span', {class :'buttonwrapper'}).insert(variantHide);
        var variantExpandTools = new Element('span', {class :'expand-tools'})
          .insert(variantShowWrapper)
          .insert(variantHideWrapper);
        item.insert({bottom: variantExpandTools});
        [variantShow, variantHide].invoke('observe', 'click', function (event) {
          variantShow.toggleClassName('v-collapsed');
          variantHide.toggleClassName('v-collapsed');
          _this._geneTable.select('.variant-hide-heading-' + geneIndex).each( function(item) {
              if (variantShow.hasClassName('v-collapsed')) {
                item.toggleClassName('v-collapsed', false);
                //collapse all more info for variants
                _this._geneTable.select('.variant-hide-heading-' + geneIndex + '.variant-moreinfo-row').each( function(item) {
                  item.toggleClassName('v-collapsed', true);
                  item.previous().down('.show-moreinfo-button').toggleClassName('triRight', true);
                  item.previous().down('.show-moreinfo-button').toggleClassName('triDown', false);
                });
              }
              else
                item.toggleClassName('v-collapsed', true);
            });
        });
      });
    },

    createEditDoneButtons : function (className) {
      var _this = this;
      this._geneTable.select(className).each(function(row) {
        var variantIndex = row.className.substring(row.className.lastIndexOf('-') + 1);
        var editVariantLink = new Element('a',
          {
            class : 'action-edit variant-edit-done v-collapsed',
            href : '#',
            id : _this.geneVariantClassName + '_' + variantIndex + '_editDone'
          });
        row.insert(editVariantLink);
      });
    },

    // restrict only numerical input for start_position and end_position variant fiellds
    restrictNumericInput : function (container) {
      if (!container) {
        container = this._geneTable;
      }
      container.select('input[id$="_start_position"]', 'input[id$="_end_position"]').each( function(input) {
        input.observe('input', function(event) {
          this.value=this.value.replace(/[^\d]/g,'');
        });
      });
    },

    createEditButtons : function (className) {
      var _this = this;
      this._geneTable.select(className).each(function(row) {
        var variantIndex = row.className.substring(row.className.lastIndexOf('-') + 1);
        var editVariantLink = new Element('a',
          {
            class : 'action-edit button secondary variant-edit fa fa-pencil',
            href : '#',
            title : "$services.localization.render('PhenoTips.GeneVariantClass.edit.hint')",
            id : _this.geneVariantClassName + '_' + variantIndex + '_edit', style :'font-size: 90%'
          });
        var editVarientWrapper = new Element('span',
          {
            class : 'buttonwrapper variant-moreinfo-button'
          }).insert(editVariantLink);
        row.insert({top : editVarientWrapper});
      });
    },

    createShowMoreinfoButtons : function (className, newVariant) {
      var _this = this;
      this._geneTable.select(className).each(function(td) {
        var variantIndex = td.className.substring(td.className.lastIndexOf('-') + 1);
        var showMoreinfoWrapper = new Element('div',
          {
            class :'show-moreinfo-button',
            title : "$services.localization.render('PhenoTips.GeneVariantClass.showMoreinfo.hint')",
            id : _this.geneVariantClassName + '_' + variantIndex + '_showMoreinfo'
          });
        showMoreinfoWrapper.addClassName((newVariant) ? 'triDown' : 'triRight');
        showMoreinfoWrapper.observe('click', function (event) {
          event.stop();
          event.element().up('tr').next().toggleClassName('v-collapsed');
          if (event.element().up('tr').next().hasClassName('v-collapsed')) {
            event.element().toggleClassName('triRight', true);
            event.element().toggleClassName('triDown', false);
          } else{
            event.element().toggleClassName('triRight', false);
            event.element().toggleClassName('triDown', true);
          }
        });
        td.insert({top : showMoreinfoWrapper});
      });
    },

    lockGeneInput : function () {
      //lock genesymbol inputs if there are variants
      var _this = this;
      this._geneTable.select('.suggested.suggest-gene.gene-name').each( function(item) {
        var className = item.up().className;
        var geneIndex = className.substring(className.lastIndexOf('-') + 1);
        //generate label
        var geneLabel = new Element('p', {class :' gene col-label gene-' + geneIndex + ' gene-input-label'});
        geneLabel.update(item.value.escapeHTML());
        item.insert({before: geneLabel});
        if (_this._geneTable.select('.variant-hide-heading-' + geneIndex).length > 0) {
          item.toggleClassName('v-collapsed', true);
        } else {
          geneLabel.toggleClassName('v-collapsed', true);
        }
      });
    },

    editData : function (event) {
      event.stop();

      var id = event.element().id;
      var variantIndex = id.substring(id.indexOf('_') + 1, id.lastIndexOf('_'));

      this._geneTable.select('tr .variant-' + variantIndex + ' tr').each( function(item) {
        item.toggleClassName('moreinfo-view', false);
      });

      event.element().toggleClassName('v-collapsed', true);
      $(this.geneVariantClassName + '_' + variantIndex + '_editDone').toggleClassName('v-collapsed', false);

      var labels = this._geneTable.select('.variant-label-' + variantIndex);
      this._geneTable.select('.variant-input-' + variantIndex).each ( function(item, index) {
        item.toggleClassName('v-collapsed', false);
        labels[index].toggleClassName('v-collapsed', true);
        labels[index].up('tr').toggleClassName('v-collapsed', false);
      });
      //set the label column width for variant more info little table
      event.element().up('td.variant.moreinfo').select('td:first-child').each ( function(item) {
        item.toggleClassName('moreinfo-table-label-width', true);
      });
    },

    editDoneData : function (event) {
      event.stop();

      var id = event.element().id;
      var variantIndex = id.substring(id.indexOf('_') + 1, id.lastIndexOf('_'));

      this._geneTable.select('tr .variant-' + variantIndex + ' tr').each( function(item) {
        item.toggleClassName('moreinfo-view', true);
      });

      event.element().toggleClassName('v-collapsed', true);
      $(this.geneVariantClassName + '_' + variantIndex + '_edit').toggleClassName('v-collapsed', false);

      var inputs = this._geneTable.select('.variant-input-' + variantIndex);
      var labels = this._geneTable.select('.variant-label-' + variantIndex);

      for (var i = 0; i < inputs.length; i++) {

        inputs[i].toggleClassName('v-collapsed', true);
        if (labels[i].className.indexOf('chromosome') >= 0 ||
            labels[i].className.indexOf('segregation') >= 0 ||
            labels[i].className.indexOf('sanger') >= 0 ||
            labels[i].className.indexOf('inheritance') >= 0 ||
            labels[i].className.indexOf('zygosity') >= 0 ||
            labels[i].className.indexOf('effect') >= 0) {
          labels[i].innerHTML = inputs[i].firstChild[inputs[i].firstChild.selectedIndex].text;
        } else if (labels[i].className.indexOf('evidence') >= 0) {
          labels[i].innerHTML = "";
          inputs[i].childElements().each( function(item) {
            if (item.tagName == "LABEL" && item.firstDescendant().checked) {
              // Get inner text with cross-browser support
              var innerText = item.textContent || item.innerText;
              labels[i].innerHTML += innerText + '; ';
            }
          });
        } else {
          labels[i].innerHTML = inputs[i].firstChild.value;
        }

        //hide row in "More Info" if empty value
        if (labels[i].innerHTML === '') {
          labels[i].up('tr').toggleClassName('v-collapsed', true);
        } else {
          labels[i].up('tr').toggleClassName('v-collapsed', false);
          labels[i].toggleClassName('v-collapsed', false);
        }
      }
      //unset the label column width for variant more info little table
      event.element().up('td.variant.moreinfo').select('tr td:first-child').each ( function(item) {
        item.toggleClassName('moreinfo-table-label-width', false);
      });
    }

  });

  return ExtraGeneVariantData;
}(ExtraGeneVariantData || {}));

document.observe('xwiki:dom:loaded', function() {
  // All gene tables should share this class name.
  var geneTables = $$('.gene-table.extradata-list');
  var tableCategories = {};
  geneTables.each(function(geneTable) {
    // If tables are displayed separately, but they share the same object, they should have a global count. These groups
    // have an additional class name that they share (e.g. "gene-table some-class-shared-by-group extradata-list").
    var className = geneTable.className;
    var byCategory = tableCategories[className];
    if (!byCategory) {
      byCategory = [];
      tableCategories[className] = byCategory;
    }
    byCategory.push(geneTable);
  });
  // Each group shares a global geneIndex and variantIndex.
  $H(tableCategories).each(function(pair) {
    var group = pair.value;
    var globalOptions = {'geneIndex' : 0, 'variantIndex' : -1};
    group.each(function(tableEl) {
      // Look at any retrieved genes for the table element, take the largest for the group.
      var rows = tableEl.select('td.row-count');
      var idx = rows.size();
      if (idx > 1) {
        var className = rows[idx - 2].next().className;
        globalOptions.geneIndex = Math.max(globalOptions.geneIndex, parseInt(className.substring(className.lastIndexOf('-') + 1), 10) + 1);
      }

      // Look at any retrieved variants for the table element, take the largest for the group.
      tableEl.select('.variant-moreinfo-editbutton-row').each( function(item) {
        var strIdx = item.className.substring(item.className.lastIndexOf('-') + 1);
        var indx = parseInt(strIdx, 10);
        indx = isNaN(indx) ? -1 : indx;
        globalOptions.variantIndex = Math.max(indx, globalOptions.variantIndex);
      });

      var options = {'global' : globalOptions};
      var geneTableId = tableEl.id;
      var classesStr = geneTableId && geneTableId.substring(geneTableId.lastIndexOf('-') + 1);
      // Get the gene variant class and gene class names.
      options.geneClassName = classesStr && classesStr.substring(0, classesStr.lastIndexOf('_'));
      options.geneVariantClassName = classesStr && classesStr.substring(classesStr.lastIndexOf('_') + 1);
      new ExtraGeneVariantData.tools.Editor(tableEl, options);
    });
    globalOptions.variantIndex++;
  });
});
