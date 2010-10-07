(ns bestinclass.feeds
  (:use    clojure.contrib.io
	   [bestinclass templates]
	   [bestinclass.wordpress :only [pick]]
	   [net.cgrand.enlive-html :exclude [flatten]])
  (:import [java.io File]
	   [java.text SimpleDateFormat]))

(defn utc-date [dt]
  (try
   (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss'Z'")
	    (.parse (SimpleDateFormat. "yyyy-MM-dd h:m:s") dt))
   (catch Exception e "2009-09-04T23:19:48Z")))

(defn generate-feed [path-to-posts]
  (let [posts  (->> (file-seq (File. path-to-posts))
		    (filter #(and (.isFile %) (.endsWith (.getName %) ".html"))))
	data   (for [post posts :let [path (.getPath post)
				      res  (html-resource post)
                                      url  (str "http://www.bestinclass.dk/"
                                                (-> path (.split "/site/") last))]]
		 (let [body     (->> (select res [:div#post :> any-node]) emit* (apply str))
		       more-pos (.indexOf body "<!--more-->")
		       main     (subs body (+ (count "<!--more-->") more-pos))
		       teaser   (subs body 0 more-pos)]
		   {:title   (pick res [:title content])
		    :link    url
		    :id      url
		    :updated (utc-date (pick res [:div#pubdate content]))
		    :excerpt teaser
		    :body    main
		    }))]
    (atom-feed (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss'+08:00'") (java.util.Date.))
		    (take 10 (sort-by :updated #(compare %2 %1) data)))))