function setCookie(name, value, days) {
  if (!days) days = 100;
  var expire = new Date();
  expire.setTime(expire.getTime() + 3600000*24*days);
  document.cookie = name + "=" + escape(value) + "; expires=" + expire.toGMTString();
}
function getCookie(name) {
  var dc = document.cookie;
  var prefix = name + "=";
  var begin = dc.indexOf("; " + prefix);
  if (begin == -1) {
    begin = dc.indexOf(prefix);
    if (begin != 0) return null;
  } else {
    begin += 2;
  }
  var end = dc.indexOf(";", begin);
  if (end == -1) end = dc.length;
  return unescape(dc.substring(begin + prefix.length, end));
}
function delCookie(name) {
  if (getCookie(name)) document.cookie = name + "=" + "; expires=Fri, 31-Dec-99 23:59:59 GMT";
}
function toggleDiv(divid) {
    if (divid == 'basicSearch') {
    	document.getElementById("searchType").value = '1';
    	$(basicSearch).toggle();
    	$(advancedSearch).toggle();
    	$(window).load();
    } else if (divid == 'advancedSearch') {
    	document.getElementById("searchType").value = '2';
    	$(basicSearch).toggle();
    	$(advancedSearch).toggle();
    }
} 
function trapKeyBoardEvents(event) {
    if (event.keyCode == 13) {
    	if(document.getElementById("pageNumber") != null && document.getElementById("pageNumber").value != '') {
    		$(".paginationHolder .goPaginate").click();
    	} else {
    	    if (document.getElementById("searchType").value == '1') {   
                postBasicSearch();
            } else if (document.getElementById("searchType").value == '2') {
                postAdvancedSearch();
            }
    	}
    }
}
function disableEnterKey(e)
{
     var key;      
     if(window.event)
          key = window.event.keyCode;
     else
          key = e.which;
     return (key != 13);
}
function clearFormElements() {
	var inputArray = document.getElementsByTagName("input");
	  for (var i = 0; i < inputArray.length; i++) {  
		var objinput =  inputArray[i];
		var type = objinput.type;
		if (type == 'text' || type == 'password')
			objinput.value = "";
		else if (type == 'checkbox' || type == 'radio')
			objinput.checked = false;                                
      }
      var selectArray = document.getElementsByTagName("select");
      for (var j = 0; j < selectArray.length; j++) { 
	      var objinput =  selectArray[j]; 
	      objinput.options.selectedIndex = 0;
      }
}
function doDateCheck(fromElementId, toElementId) {
    try {
        var s_fromdate = jQuery.trim($('#' + fromElementId).val());
        var s_todate = jQuery.trim($('#' + toElementId).val());            
        if (s_fromdate.length ==0  && s_todate.length ==0) {
            return true;
        } else if (s_todate.length !=0 && s_fromdate.length ==0) {
        	$.prompt("Please Enter From date.");
            //setTimeout(function() {$('#' + fromElementId).focus();}, 0);
            return false;
        } else if ((Date.parse(s_fromdate) > Date.parse(s_todate)) && s_fromdate.length !=0 && s_todate.length !=0 ) {
            $.prompt("To date must occur after the from date.");
            //setTimeout(function() {$('#' + toElementId).focus();}, 0);
            return false;
        }  
        return true;
    } catch (err) {
        $.prompt(err.message);
    }
}
$(function(){
    $('.box')
    .each(function(){
        $(this).hover(function(){
        	$(this).find('span').removeClass('ui-icon ui-icon-triangle-1-s');
        	$(this).find('span').addClass('ui-icon ui-icon-triangle-1-e');            
        }, function(){
        	$(this).find('span').removeClass('ui-icon ui-icon-triangle-1-e');
        	$(this).find('span').addClass('ui-icon ui-icon-triangle-1-s');            
        })
        .click(function(){
             $(this).siblings('.multi_select').toggle();
        })
        .end()
    });
});
function IsNumeric(x) {
	$('#' + x).removeClass('invalid');
	try {
		var b = jQuery.trim($("#"+x).val());
		if (b.length > 0) {
			if ((b.match(/^\d*\.{0,1}\d*$/)) || (b.match(/^-\d*\.{0,1}\d*$/))) {
			} else {
				$.prompt("Invalid Number");
				$('#' + x).addClass('invalid');
				//setTimeout(function() {$('#' + x).focus();}, 0);
			}
		}
	} catch (err) {
		$.prompt(err.message);
	}
}
function doNumericCheck(x, y) {
	$('#' + x).removeClass('invalid');
	$('#' + y).removeClass('invalid');
	try {
		var b = jQuery.trim($("#"+x).val());
		var c = jQuery.trim($("#"+y).val());
		if (b.length == 0 && c.length == 0) {
			return true;
		}
		if (parseInt(c) < parseInt(b)) {
			$.prompt("To value should be more than the from value");
			$('#' + y).addClass('invalid');			
			//setTimeout(function() {$('#' + y).focus();}, 0);
			return false;
		}
		if (!(b.match(/^\d*\.{0,1}\d*$/) || b.match(/^-\d*\.{0,1}\d*$/))) {
			$.prompt("Invalid Range Value");
			$('#' + x).addClass('invalid');			
			//setTimeout(function() {$('#' + x).focus();}, 0);
			return false;
		} else if (!(c.match(/^\d*\.{0,1}\d*$/) || c.match(/^-\d*\.{0,1}\d*$/))) {
			$.prompt("Invalid Range Value");
			$('#' + y).addClass('invalid');			
			//setTimeout(function() {$('#' + y).focus();}, 0);
			return false;
		}
		return true;
	} catch (err) {
		$.prompt(err.message);
	}   		
}
function getDateQuery(name) {
    var frmDate = document.getElementById('frm' + name).value;
    var toDate  = document.getElementById('to' + name).value;
    var searchString = '';
    if (jQuery.trim(frmDate).length != 0 && jQuery.trim(toDate).length != 0) {
    	searchString = frmDate.substring(6, 10) + frmDate.substring(-1, 2) + frmDate.substring(3, 5);
    	searchString = name + ':' + '[' + searchString + ' TO ' +  toDate.substring(6, 10) + toDate.substring(-1, 2) + toDate.substring(3, 5) + ']';
    } else if (jQuery.trim(frmDate).length != 0) {
    	searchString = name + ':' + frmDate.substring(6, 10) + frmDate.substring(-1, 2) + frmDate.substring(3, 5);
    } else if (jQuery.trim(toDate).length != 0) {
    	searchString = name + ':' + toDate.substring(6, 10) + toDate.substring(-1, 2) + toDate.substring(3, 5);
    }
    return searchString;        
}

function getDateSearchQuery(name) {
    var frmDate = document.getElementById('frm' + name).value;
    var toDate  = document.getElementById('to' + name).value;
    var searchQueryString = '';
    if (jQuery.trim(frmDate).length != 0 && jQuery.trim(toDate).length != 0) {
    searchQueryString = document.getElementById('frm' + name).title + ':' + '[' + frmDate + ' TO ' +  toDate + ']';
    } else if (jQuery.trim(frmDate).length != 0) {
    searchQueryString = name + ':' + frmDate;
    } else if (jQuery.trim(toDate).length != 0) {
    searchQueryString = name + ':' + toDate;
    }
    return searchQueryString;
}
function getRangeQuery(c, cond){
	var doubleQuotes = '"';
	var d=document.getElementById("frm"+c).value;
	var a=document.getElementById("to"+c).value;
	var b="";
	if(jQuery.trim(d).length!=0 && jQuery.trim(a).length!=0){
		b=d; 
		if(cond=="Inclusive") {
			b=c+":[|"+b+"| TO |"+a+"|]"; 
		} else {
			b=c+":{|"+b+" TO "|+a+"|}";
		}
	} else if(jQuery.trim(d).length!=0){
		b=c+":|"+ d + "|";
	} else if(jQuery.trim(a).length!=0){
		b=c+":|"+a+"|";
	}	
	return b;
}
function getRangeSearchQuery(b, cond){
	var d=document.getElementById("frm"+b).value;
	var a=document.getElementById("to"+b).value;
	var c="";
	if(jQuery.trim(d).length!=0&&jQuery.trim(a).length!=0){
		if(cond=="Inclusive"){
			c=document.getElementById("frm"+b).title + ":["+d+" TO "+a+"]";
		} else {
			c=document.getElementById("frm"+b).title + ":{"+d+" TO "+a+"}";
		}
	}else if(jQuery.trim(d).length!=0){
		c=b+":"+d;
	} else if(jQuery.trim(a).length!=0){
		c=b+":"+a;
	}
	return c;
}
function gotoPage(pages, length, url) {
	  var a = $("#pageNumber").val();
	  if (a != "") {
		  if(!(a.match(/^\d*\.{0,1}\d*$/)||a.match(/^-\d*\.{0,1}\d*$/))){
		  	$.prompt('Invalid Page Number',{buttons:{Ok:true},	prefix:'extblue'});
		  } else {
		  	if (($("#pageNumber").val() * 1) <= pages && ($("#pageNumber").val() * 1) > 0) {  	
			  	var url = url + (($("#pageNumber").val() - 1) * length);
			  	window.location = url;
		  	} else {
		  		$.prompt('Entered Page Number is out of range',{buttons:{Ok:true},	prefix:'extblue'});  		
		  	}
		  }
	  } else {
	  	$.prompt('Please enter a page number',{buttons:{Ok:true},	prefix:'extblue'});
	  }
}