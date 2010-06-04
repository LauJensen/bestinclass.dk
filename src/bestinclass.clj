(ns bestinclass
  (:use    clojure.contrib.io
	   [bestinclass wordpress templates feeds])
  (:import [java.io File]))

;; Specific to bestinclass.dk/blog.
;; Extracts excerpts by reading the body up to the <!--more--> tag.
;; Converts links by stripping the first 2 segments delimited by /seg/
;; and changing /index.php/ to /index.clj/
(defn post-capture-hook [item]
  (letfn [(drop-segments [lnk n] (->> (.split lnk "/") (drop (+ 2 n)) (interpose "/")
				      (apply str)))]
    (let [{:keys [body link date]} item]
      (-> item
	  (assoc :excerpt (-> body (.split "<!--more-->") first))
	  (assoc :link    (str "/" (.replace (drop-segments link 1) ".php" ".clj") ".html"))))))

(defn generate-site []
  (let [title   "Best In Class - Software Innovator"
	css     ["/css/main.css"]
	scripts ["/scripts/jquery.tools.min.js"
		 "/scripts/jquery.fancyzoom.js"
		 "/scripts/menu.js"]
	posts (convert-posts "wp.xml" post-capture-hook)]
    (.mkdir (File. "site/"))
    (doseq [[url content] [["services.html"  (page title scripts css (services))]
			   ["produkter.html" (page title scripts css (produkter))]
			   ["kontakt.html"   (page title scripts css (kontakt))]
			   ["blog.html"      (page title
						   (conj scripts "/scripts/jquery.pajinate.js")
						   (conj css "/css/blog.css")
						   (blog posts))]
			   ["index.html"     (page title (conj scripts "/scripts/frontpage.js")
						   (conj css
							 "/css/main.css"
							 "/css/scrollable.css")
						   (frontpage))]]
	    :let [uri (File. (str "site/" url))]]
      (println "Generating: " (.getName uri))
      (spit uri (apply str content)))
    (println "Generating atom feed")
    (->> (generate-feed "site/index.clj/")
	 (apply str)
	 (spit "site/atom.xml"))
    (println "Site generated.\n\n")))


