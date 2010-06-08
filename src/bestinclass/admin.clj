(ns bestinclass.admin
  (:use [net.cgrand.enlive-html :exclude [flatten]]
	net.cgrand.moustache
	ring.util.response ring.middleware.file	ring.adapter.jetty
	[clojure.contrib io shell]
	[bestinclass comments feeds templates])
  (:import [java.io File]
	   [java.util Calendar Date]
	   [java.text SimpleDateFormat]))

(declare wroutes)

 					;:> GLOBALS

;(def server (doto (Thread. #(run-jetty #'wroutes {:port 8080 :host "127.0.0.1"})) .start))

(defn static [tplate] (-> tplate response constantly))


					;:> FS IO

(defn parse-args [a]
  (-> (.split a "=") last java.net.URLDecoder/decode))

(defn read-body [body]
  (try
   (apply str (map char (take-while pos? (repeatedly #(.read body)))))
   (catch Exception e
     500)))

(defn kill-on-disk [id]
  (let [queue    (slurp "comment-queue")
	comments (if-not (empty? queue)
		   (map #(assoc (read-string %2) :id (str %1)) (iterate inc 0) (.split queue "\n"))
		   [])]
    (spit "comment-queue" (with-out-str
			    (doseq [comment comments]
			      (when (not= id (:id comment))
				(prn comment)))))))

(defn load-from-disk [id]
  (let [queue (-> (slurp "comment-queue") (.split "\n"))]
    (nth (map read-string queue) (Integer/parseInt id))))

(defn get-avatars []
  (->> (file-seq (File. "resources/wp-content/uploads/avatars/"))
       (filter #(.isFile %))
       (map #(.getName %))))

					;:> LOGIC

(defn older-than? [days dt]
  (let [cal      (Calendar/getInstance)]
    (.add cal Calendar/DATE (- days))
    (pos? (.compareTo dt (.getTime cal)))))

(defn strip-www [url]
  (-> (.split url (if (.contains url "www.")
		    "www.bestinclass.dk"
		    "bestinclass.dk"))
      last
      (.split "/")))

(defn append-to-post [{:keys [url name date comment]}]
  (let [url       (->> (strip-www url) (concat ["site"]) (interpose "/") (apply str))
	url2      (str url (hash url))
	c-class   (if (= name "Lau") "comment-lau" "comment")]
    (->> ((template (-> url File. html-resource) [new-comment]
		    [:div#debate] (append new-comment))
	  (a-comment c-class name date comment))
	 (apply str)
	 (spit url2))
    (sh "mv" url2 url)))

					;:> WEB BACKEND

(defn parse-comment [{body :body}]
  (let [content   (read-body body)]
    (if (string? content)
      (receive-comment content)
      {:body content})))

(defn kill-comment [id]
  (do (kill-on-disk id)
      (constantly (redirect "/admin"))))

(defn approve-comment [id url]
  (let [comment (load-from-disk id)] ; TODO: Eliminate race condition
    (kill-on-disk id)
    (append-to-post comment)
    (constantly (redirect "/admin"))))

(defn save-draft [{body :body}]
  (let [content   (-> (read-body body) parse-args)]
    (spit "draft" content)
    (redirect "/editor")))

(defn publish-post [{body :body}]
  (let [{:keys [title avatar]}
	(into {}
	      (for [item (.split (read-body body) "&")
		    :let [item (.split item "=")]]
		{(keyword (first item))
		 (java.net.URLDecoder/decode (last item))}))
	avatar    (str "/" avatar)
	draft     (slurp "draft")
	filename  (-> title (.replaceAll " " "-") (.replaceAll "--" "-") .toLowerCase)
	date      (.format (java.text.SimpleDateFormat. "yyyy-MM-dd hh:mm:ss") (java.util.Date.))
	url       (format "/index.clj/%s/%s/%s.html"
			  (.format (java.text.SimpleDateFormat. "yyyy") (java.util.Date.))
			  (.format (java.text.SimpleDateFormat. "MM") (java.util.Date.))
			  filename)]
    (make-parents (File. (str "site" url)))
    (->> (page (str "Best In Class: " title)
	       ["/scripts/jquery.tools.min.js" "/scripts/menu.js" "/scripts/post.js"]
	       ["/css/main.css" "/css/blog.css" "/css/comment-form.css"]
	       (post {:title    title
		      :body     draft
		      :thumb    avatar
		      :date     date
		      :comments []
		      :link     (str "http://www.bestinclass.dk/" url)}))
	 (apply str)
	 (spit (str "site" url)))
    (->> ((template (File. "site/blog.html") [title link thumb excerpt]
		    [:ul.content] (prepend (select (teaser title link (str "/" thumb) excerpt)
						   [:ul :> any-node])))
	  title url avatar (-> (.split (slurp "draft") "<!--more-->") first))
	 (apply str)
	 (spit "site/blog.html"))
    (->> (generate-feed "site/index.clj/")
	 (apply str)
	 (spit "site/atom.xml"))
    (redirect "/blog.html")))

(defn read-access-log []
  (let [dtformat (SimpleDateFormat. "[dd/MMM/yyyy:HH:mm:ss")
	parse-ex #"[^\s\"]\S*|\"[^\"]*\""
        entries  (->> (read-lines "access.log")
		      (map #(re-seq parse-ex #^String %))
		      (filter #(let [request (nth % 5)]
				 (or (.contains #^String request "GET / ")
				     (.contains #^String request "html"))))
		      (map #(let [[ip _ _ date _ request code size referer agent] %]
			      {:ip      ip
			       :date    (.parse dtformat date)
			       :request request
			       :code    code
			       :size    size
			       :referer (.replaceAll referer "\"" "")
			       :agent   agent})))]
    entries))
					;:> WEB UI

(defn render-admin-interface [_]
  (let [queue    (slurp "comment-queue")
	avatars  (get-avatars)
	comments (if-not (empty? queue)
		   (map #(assoc (read-string %2) :id %1)
			(iterate inc 0)
			(.split queue "\n")) [])
	entries  (read-access-log)
	today    (-> (filter #(older-than? 1  (:date %)) entries) count str)
	week     (-> (filter #(older-than? 7  (:date %)) entries) count str)
	month    (-> (filter #(older-than? 30 (:date %)) entries) count str)
	referers (->> (reverse entries)
		      (map (juxt :referer :date))
		      (remove #(let [r (first %)]
				 (or (= "-" r)
				     (.contains r "bestinclass")
				     (.contains r "www.google."))))
		      (take 100))]
    (content-type
     (response (admin-page (slurp "draft")
			   avatars
			   comments
			   today
			   week
			   month
			   referers))
     "text/html; charset=UTF-8")))

(defn render-editor [_]
  (content-type
   (response (page "Best In Class: New post"
		   ["/ckeditor/ckeditor.js"]
		   ["/css/main.css"]
		   (editor (slurp "draft"))))
    "text/html; charset=UTF-8"))

(defn render-comment-form [url]
  (static (comment-form (apply str (interpose "/" (rest url)))
			(rand-nth bestinclass.comments/captchas))))

(def wroutes
     (app
      (wrap-file "resources")
      ["admin"]            render-admin-interface
      ["editor"]           render-editor
      ["dispose" id]       (kill-comment    id)
      ["approve" id & url] (approve-comment id url)

      ["cmt" & url]        {:get  (render-comment-form url)
			    :post parse-comment}

      ["recv"]             {:post save-draft}
      ["publish"]          {:post publish-post}))

(def backup-agent (agent 0))
(send-off backup-agent backup-comments)

(run-jetty wroutes {:port 8080 :host "127.0.0.1"})