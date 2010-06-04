function animate_bg(ele, from, to) {
    ele.css("background-color", "rgba(50, 50, 50, " + (from += from > to ? -1 : 1) / 10 + ")");
    if(from != to)
	setTimeout(function() { animate_bg(ele, from, to) }, 40);
}

$(document).ready(function(){
	$("div#pages a img").hover(function() {
		return animate_bg($(this), 0, 10);
	    }, function() {
		return animate_bg($(this), 10, 0);
	    });
    });
