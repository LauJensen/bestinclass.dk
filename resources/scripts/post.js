$(document).ready(function() {

	$("img[title]").tooltip();

	$("a[rel]").overlay({
		mask: '#ccc',
		    effect: 'apple',
		    onBeforeLoad: function() {
		    var wrap = this.getOverlay().find(".contentWrap");
		    wrap.load(this.getTrigger().attr("href"));
		}

	    });

	$('div#debate').children().hide();

        $('a#showdiscussion').click(function() {
		$('div#debatefooter').fadeIn(200);
		$('div#debateheader').fadeIn(200);

		$('div#debate').animate({height: '400px'}).children().show();

		return false;
	    });
    });
