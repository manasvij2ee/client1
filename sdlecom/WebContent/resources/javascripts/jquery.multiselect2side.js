/*
 * multiselect2side jQuery plugin
 *
 * Copyright (c) 2010 Giovanni Casassa (senamion.com - senamion.it)
 *
 * Dual licensed under the MIT (MIT-LICENSE.txt)
 * and GPL (GPL-LICENSE.txt) licenses.
 *
 * http://www.senamion.com
 *
 */

(function($)
{
	jQuery.fn.multiselect2side = function (o) {

		o = $.extend({
			selectedPosition: 'right',
			moveOptions: true,
			labelTop: 'Top',
			labelBottom: 'Bottom',
			labelUp: 'Up',
			labelDown: 'Down'
		}, o);


		return this.each(function () {
			var	el = $(this);

			var	originalName = $(this).attr("name");
			if (originalName.indexOf('[') != -1)
				originalName = originalName.substring(0, originalName.indexOf('['));

			var	nameDx = originalName + "ms2side__dx";
			var	nameSx = originalName + "ms2side__sx";
			var size = $(this).attr("size");
			// SIZE MIN
			if (size < 6) {
				$(this).attr("size", "6");
				size = 6;
			}

			// UP AND DOWN
			var divUpDown =
					"<div class='ms2side__updown'>" +
						"<div class='MoveTop' title='Move on top selected option'></div>" +
						"<div class='MoveUp' title='Move up selected option'></div>" +
						"<div class='MoveDown' title='Move down selected option'></div>" +
						"<div class='MoveBottom' title='Move on bottom selected option'></div>" +
					"</div>";

			// CREATE NEW ELEMENT (AND HIDE IT) AFTER THE HIDDED ORGINAL SELECT
			var htmlToAdd = 
				"<div class='ms2side__div'>" +
						((o.selectedPosition != 'right' && o.moveOptions) ? divUpDown : "") +
					"<div class='ms2side__select'>" +
						"<select name='" + nameSx + "' id='" + nameSx + "' size='" + size + "' multiple='multiple' ></select>" +
					"</div>" +
					"<div class='ms2side__options'>" +
						((o.selectedPosition == 'right')
						?
						("<div class='AddOne' title='Add Selected'></div>" +
						"<div class='AddAll' title='Add All'></div>" +
						"<div class='RemoveOne' title='Remove Selected'></div>" +
						"<div class='RemoveAll' title='Remove All'></div>")
						:
						("<p class='AddOne' title='Add Selected'></p>" +
						"<p class='AddAll' title='Add All'></p>" +
						"<p class='RemoveOne' title='Remove Selected'></p>" +
						"<p class='RemoveAll' title='Remove All'></p>")
						) +
					"</div>" +
					"<div class='ms2side__select'>" +
						"<select name='" + nameDx + "' id='" + nameDx + "' size='" + size + "' multiple='multiple' ></select>" +
					"</div>" +
					((o.selectedPosition == 'right' && o.moveOptions) ? divUpDown : "") +
				"</div>";
			$(this).after(htmlToAdd).hide();
			
			// ELEMENTS
			var allSel = $(this).next().find("select");
			var	leftSel = (o.selectedPosition == 'right') ? allSel.eq(0) : allSel.eq(1);
			var	rightSel = (o.selectedPosition == 'right') ? allSel.eq(1) : allSel.eq(0);

			// MOVE SELECTED OPTION TO RIGHT, NOT SELECTED TO LEFT
			$(this).find("option:selected").clone().appendTo(rightSel);
			$(this).find("option:not(:selected)").clone().appendTo(leftSel);

			// SELECT FIRST LEFT ITEM
			leftSel.find("option").eq(0).attr("selected","selected");

			// ON CHANGE REFRESH ALL BUTTON STATUS
			allSel.change(function() {
				var	div = $(this).parent().parent();
				var	selectSx = leftSel.children();
				var	selectDx = rightSel.children();
				var	selectedSx = leftSel.find("option:selected");
				var	selectedDx = rightSel.find("option:selected");

				if (selectedSx.size() == 0)
					div.find(".AddOne").addClass('ms2side__hide');
				else
					div.find(".AddOne").removeClass('ms2side__hide');

				// FIRST HIDE ALL
				div.find(".RemoveOne, .MoveUp, .MoveDown, .MoveTop, .MoveBottom").addClass('ms2side__hide');
				if (selectedDx.size() > 0) {
					div.find(".RemoveOne").removeClass('ms2side__hide');
					// ALL SELECTED - NO MOVE
					if (selectedDx.size() < selectDx.size()) {	// FOR NOW (JOE) && selectedDx.size() == 1
						if (selectedDx.val() != selectDx.val())	// FIRST OPTION, NO UP AND TOP BUTTON
							div.find(".MoveUp, .MoveTop").removeClass('ms2side__hide');
						if (selectedDx.last().val() != selectDx.last().val())	// LAST OPTION, NO DOWN AND BOTTOM BUTTON
							div.find(".MoveDown, .MoveBottom").removeClass('ms2side__hide');
					}
				}

				if (selectSx.size() == 0)
					div.find(".AddAll").addClass('ms2side__hide');
				else
					div.find(".AddAll").removeClass('ms2side__hide');

				if (selectDx.size() == 0)
					div.find(".RemoveAll").addClass('ms2side__hide');
				else
					div.find(".RemoveAll").removeClass('ms2side__hide');
			});

			// CLICK ON OPTION
			$(this).next().find('.ms2side__options').children().click(function () {
				if ($(this).hasClass("AddOne")) {
					leftSel.find("option:selected").each(function(i, selected){
						$(this).remove().appendTo(rightSel);
						el.find("[value=" + $(selected).val() + "]").attr("selected","selected");
					});
				}
				else if ($(this).hasClass("AddAll")) {	// ALL SELECTED
					leftSel.children().appendTo(rightSel);
					leftSel.children().remove();
					el.children().attr("selected","selected");
					sortDropDownListByText();
				}
				else if ($(this).hasClass("RemoveOne")) {
					rightSel.find("option:selected").each(function(i, selected){
						$(this).remove().appendTo(leftSel);
						el.find("[value=" + $(selected).val() + "]").attr("selected","");
					});
				}
				else if ($(this).hasClass("RemoveAll")) {	// ALL REMOVED
					rightSel.children().appendTo(leftSel);
					rightSel.children().remove();
					el.children().attr("selected","");
					sortDropDownListByText();
				}

				leftSel.trigger('change');
			});
			
			// DOUBLE CLICK ON LEFT SELECT OPTION
			leftSel.dblclick(function () {
				var	selectedDx = rightSel.find("option:selected");
				var	selectDx = rightSel.find("option");

				$(this).find("option:selected").each(function(i, selected){
					$(this).remove().appendTo(rightSel);
					el.find("[value=" + $(selected).val() + "]").attr("selected","selected");
				});
				
				/*var	next = selectedDx.last().next();
				var	hNext = el.find("[value=" + next.val() + "]");

				selectedDx.each(function() {
					$(this).insertAfter(next);
					el.find("[value=" + $(this).val() + "]").insertAfter(hNext);	// HIDDEN SELECT
				});*/
				
				$(this).trigger('change');
			});

			// DOUBLE CLICK ON RIGHT SELECT OPTION
			rightSel.dblclick(function () {
				$(this).find("option:selected").each(function(i, selected){
					$(this).remove().appendTo(leftSel);
					el.find("[value=" + $(selected).val() + "]").attr("selected","");
				});
				$(this).trigger('change');
			});

			// CLICK ON UP - DOWN
			$(this).next().find('.ms2side__updown').children().click(function () {
				var	selectedDx = rightSel.find("option:selected");
				var	selectDx = rightSel.find("option");

				if ($(this).hasClass("ms2side__hide"))
					return;

				if ($(this).hasClass("MoveUp")) {
					var	prev = selectedDx.first().prev();
					var	hPrev = el.find("[value=" + prev.val() + "]");

					selectedDx.each(function() {
						$(this).insertBefore(prev);
						el.find("[value=" + $(this).val() + "]").insertBefore(hPrev);	// HIDDEN SELECT
					});
				}
				else if ($(this).hasClass("MoveDown")) {
					var	next = selectedDx.last().next();
					var	hNext = el.find("[value=" + next.val() + "]");

					selectedDx.each(function() {
						$(this).insertAfter(next);
						el.find("[value=" + $(this).val() + "]").insertAfter(hNext);	// HIDDEN SELECT
					});
				}
				else if ($(this).hasClass("MoveTop")) {
					var	first = selectDx.first();
					var	hFirst = el.find("[value=" + first.val() + "]");

					selectedDx.each(function() {
						$(this).insertBefore(first);
						el.find("[value=" + $(this).val() + "]").insertBefore(hFirst);	// HIDDEN SELECT
					});
				}
				else if ($(this).hasClass("MoveBottom")) {
					var	last = selectDx.last();
					var	hLast = el.find("[value=" + last.val() + "]");

					selectedDx.each(function() {
						last = $(this).insertAfter(last);	// WITH last = SAME POSITION OF SELECTED OPTION AFTER MOVE
						hLast = el.find("[value=" + $(this).val() + "]").insertAfter(hLast);	// HIDDEN SELECT
					});
				}

				leftSel.trigger('change');
			});

			// HOVER ON OPTION
			$(this).next().find('.ms2side__options, .ms2side__updown').children().hover(
				function () {
					$(this).addClass('ms2side_hover');
				},
				function () {
					$(this).removeClass('ms2side_hover');
				}
			);

			// UPDATE BUTTON ON START
			leftSel.trigger('change');
			// SHOW WHEN ALL READY
			$(this).next().show();
		});
	};
})(jQuery);