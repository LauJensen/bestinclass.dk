(ns bestinclass.admin
  (:use [net.cgrand.enlive-html :exclude [flatten]]
	net.cgrand.moustache
	ring.util.response
        ;ring.adapter.jetty
        [ring.middleware params file]
	[clojure.contrib shell]
        [clojure.contrib.io :exclude [spit]]
	[bestinclass comments feeds templates shared])
  (:import [java.io File]
	   [java.util Calendar Date]
	   [java.text SimpleDateFormat]))

(declare wroutes)

 					;:> GLOBALS

;(def server (doto (Thread. #(run-jetty #'wroutes {:port 8080 :host "127.0.0.1"})) .start))

(defn static [tplate] (-> tplate response constantly))

					;:> FS IO

(defn comments-as-seq []
  (let [queue (slurp (in-tomcat "comment-queue"))]
    (map #(assoc %2 :id %1) (range) (read-string (str \[ queue \])))))

(defn parse-args [a]
  (-> (.split a "=") last java.net.URLDecoder/decode))

(defn read-body [body]
  (try
   (apply str (map char (take-while pos? (repeatedly #(.read body)))))
   (catch Exception e
     500)))

(defn kill-on-disk [id]
  (let [queue    (comments-as-seq)]
    (spit (in-tomcat "comment-queue")
          (with-out-str
            (doseq [comment queue]
              (when (not= (Integer. id) (:id comment))
                (pr comment)))))))

(defn load-from-disk [id]
  (let [queue (comments-as-seq)]
    (nth queue (Integer/parseInt id))))

(defn get-avatars []
  (->> (file-seq (File. (in-tomcat "site/wp-content/uploads/avatars/")))
       (filter #(.isFile %))
       (map #(.getName %))
       sort ))
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
  (let [url       (in-tomcat (->> (strip-www url) (concat ["site"]) (interpose "/") (apply str)))
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
  (kill-on-disk id)
  (constantly (redirect "/admin")))

(defn approve-comment [id url]
  (let [comment (load-from-disk id)] ; TODO: Eliminate race condition
    (kill-on-disk id)
    (append-to-post comment)
    (constantly (redirect "/admin"))))

(defn raw?
  [body]
  (not
   (-> (.split body "<!--more-->")
       first
       (.contains "</div>"))))

(defn add-header
  " Adds a box which puts space between the preface and the body "
  [body]
  (let [[preface text] (.split body "<!--more-->")]
    (apply str
           (concat (str "<div id=\"box\">" preface "</div>")
                   "\n<!--more-->\n"
                   text))))

(defn save-draft [{body :body}]
  (let [content   (-> (read-body body) parse-args)]
    (spit (in-tomcat "draft")
          (if (raw? content)
            (add-header content)
            content))
    (redirect "/editor")))

(defn publish-post [{body :body}]
  (let [{:keys [title avatar]}
	(into {}
	      (for [item (.split (read-body body) "&")
		    :let [item (.split item "=")]]
		{(keyword (first item))
		 (java.net.URLDecoder/decode (last item))}))
	avatar    (str "/" avatar)
	draft     (apply str (concat (slurp (in-tomcat "draft")) (slurp (in-tomcat "author"))))
	filename  (-> title (.replaceAll " " "-") (.replaceAll "--" "-") .toLowerCase)
	date      (.format (java.text.SimpleDateFormat. "yyyy-MM-dd hh:mm:ss") (java.util.Date.))
	url       (format "/index.clj/%s/%s/%s.html"
			  (.format (java.text.SimpleDateFormat. "yyyy") (java.util.Date.))
			  (.format (java.text.SimpleDateFormat. "MM") (java.util.Date.))
			  filename)]
    (make-parents (File. (str static-root  "site" url)))
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
	 (spit (str static-root "site" url)))
    (->> ((template (File. (in-tomcat "site/blog.html")) [title link thumb excerpt]
		    [:ul.content] (prepend (select (teaser title link (str "/" thumb) excerpt)
						   [:ul :> any-node])))
	  title url avatar (-> (.split (slurp (in-tomcat "draft")) "<!--more-->") first))
	 (apply str)
	 (spit (in-tomcat "site/blog.html")))
    (->> (generate-feed (in-tomcat "site/index.clj/"))
	 (apply str)
	 (spit (in-tomcat "site/atom.xml")))
    (redirect "/blog.html")))

(defn intersperse [c strng]
  (apply str (interpose c strng)))

(defn generate-barchart-url [stats]
  (let [stats    (take-last 25 stats)
        base-url "http://chart.apis.google.com/chart?"
	[vs ks]  [(vals stats) (map #(apply str (drop 5 %)) (keys stats))]]
    (if-not (seq vs)
      ""
      (format "%scht=lc&chs=800x250&chbh=a,15,15&chf=c,s,222222|bg,s,121212&chxs=0,FFFFFF,10,0,t&chg=20,50,1,5&chm=N,FFFFFF,0,-1,11&chxt=x,y&chd=t:%s&chxl=0:|%s|1:|%s&chxr=0,%s&chds=0,%s"
              base-url
              (intersperse "," vs)
              (intersperse "|" ks)
              (intersperse "|" (map int  [(apply min vs)
                                          (/ (reduce + vs) (count vs))
                                          (apply max vs)]))
              (str (apply max vs))
              (str (apply max vs))))))

(defn read-history []
  (if (.isFile (java.io.File. (in-tomcat "stats")))
    (-> (in-tomcat "stats") slurp read-string)
    {:referers [] :hits []}))

(defn parse-log [filename]
  (let [dtformat     (SimpleDateFormat. "[dd/MMM/yyyy:HH:mm:ss")
	parse-ex     #"[^\s\"]\S*|\"[^\"]*\""]
    (->> (read-lines filename)
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
		  :agent   agent})))))

(defn merge-referers [log-entries referers]
  (let [dtformat        (SimpleDateFormat. "MM-dd HH:mm:ss")
	from-access-log (->> (map (juxt :referer #(.format dtformat (:date %))) log-entries)
			     (sort-by :date)
			     (remove #(let [r (first %)]
					(or (= "-" r)
					    (.contains r "bestinclass")
					    (.contains r "www.google."))))
			     (take 100))]
    (if (= 100 (count from-access-log))
      from-access-log
      (concat referers from-access-log))))

(defn serialdate [d]
  (let [serialdate   (SimpleDateFormat. "yyyy-MM-dd")
	old-date     (:date d)]
    (assoc d :date (.format serialdate old-date))))

(defn compile-stats
  "Moves the access.log into an archive file, named access.log.n where n is an
   incremental counter. The log is then parsed and merged with the historical data
   in the file 'stats' and the result is returned"
  []
  (if (.isFile (File. (in-tomcat "access.log")))
    (let [{:keys [referers hits]} (read-history)
	  archive-name (->> (iterate inc 1)
			    (map #(File. (str static-root "logs/access.log." %)))
			    (remove #(.isFile %))
			    first
			    .getName
			    (format "%slogs/%s" static-root))
	  result       (do (sh "mv" (str static-root "access.log") archive-name)
			   (sh "sudo" "killall" "-USR1" "nginx"))
                                        ; Reopen logs, otherwise access.log wouldn't be used
	  log-entries  (parse-log archive-name)
	  hit-stats    (into {} (for [day (sort-by :date (group-by :date (map serialdate log-entries)))]
				  {(key day) (count (val day))}))
	  ref-stats    (merge-referers log-entries referers)
	  stats        {:referers ref-stats
			:hits     (merge-with + hit-stats hits)}]
      (spit (str static-root "stats") (with-out-str (prn stats)))
      stats)
    (read-string (slurp (str static-root "stats")))))

					;:> WEB UI

(defn get-articles []
  (for [f (file-seq (java.io.File. (in-tomcat "site/index.clj/")))
        :let   [fname (.getName f) abspath (.getAbsolutePath f)]
        :when  (and (.isFile f)
                    (= ".html" (subs fname (.lastIndexOf fname "."))))]
    [fname abspath]))

(defn render-admin-interface [_]
  (let [{:keys [referers hits]} (compile-stats)
	avatars  (get-avatars)]
    (content-type
     (response (admin-page (map first (get-articles))
                           (apply str (slurp (in-tomcat "draft")) (slurp (in-tomcat "author")))
			   avatars
			   (comments-as-seq)
			   (generate-barchart-url (sort-by first hits))
			   (sort-by last #(compare %2 %1) referers)))
     "text/html; charset=UTF-8")))

(defn render-editor [_]
  (content-type
   (response (page "Best In Class: New post"
		   ["/ckeditor/ckeditor.js"]
		   ["/css/main.css"]
		   (editor (slurp (in-tomcat "draft")))))
    "text/html; charset=UTF-8"))

(defn render-comment-form [url]
  (static (comment-form (apply str (interpose "/" (rest url)))
			(rand-nth bestinclass.comments/captchas))))

 (defn ul-avatar
  "Receives an avatar via POST form submission"
  [r]
  (try
   (println :info "Receiving file")
   (let [[_ filename] (re-find #"qqfile=(.*)" (-> r :query-string))
         path     (str static-root "site/wp-content/uploads/avatars/"
                       (-> filename
                           (.replaceAll "%20" "")
                           (.replaceAll "&" "")))
         l        (println :info (str "path: " path))
         dest     (File. path)]
     (println :info "Making parents")
     (make-parents   dest)
     (println :info "Copying")
     (copy (:body r) dest)
     (println :info "Copy complete")
     (response "{success:true}"))
   (catch Exception e
     (println :warn (.getMessage e)))))

(defn find-article
  [s]
  (-> (filter #(.contains % s) (map last (get-articles)))
      first))

(defn fetch-article
  [{params :query-params}]
  (let [article (find-article (params "name"))
        content (-> article java.io.File. html-resource
                    (select [:div#post]) emit*)]
    (content-type (response content) "text/html; charset=UTF-8")))

(defn submit-article
  [{params :form-params}]
  (let [post    (params "content")
        article (File. (find-article (params "name")))]
    (->> ((template article [c] [:div#post]
                    (-> (html-snippet c)
                        (select [:div#post :> any-node])
                        content)) post)
         (apply str)
         (spit article)))
  (response "OK"))

(def wroutes
     (app
      ["bestinclass" &]
      (app
       ["admin"]            render-admin-interface
       ["editor"]           render-editor
       ["dispose" id]       (kill-comment    id)
       ["approve" id & url] (approve-comment id url)

       ["fetch"]            (wrap-params fetch-article)
       ["submit"]           (wrap-params submit-article)

       ["cmt" & url]        {:get  (render-comment-form url)
                             :post parse-comment}

       ["ul"]               {:post ul-avatar}
       ["recv"]             {:post save-draft}
       ["publish"]          {:post publish-post})))

(def backup-agent (agent 0))
(send-off backup-agent backup-comments)

#_(defn start-server []
  (doto (Thread. #(run-jetty #'wroutes {:port 8080 :host "127.0.0.1"}))
    .start))