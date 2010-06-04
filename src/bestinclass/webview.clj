(ns bestinclass.webview
  #^{:author "Lau B. Jensen"
     :doc    "This is strictly for fast prototyping of templates and conversion
              functions"}
  (:use [net.cgrand.enlive-html :exclude [flatten]]
	net.cgrand.moustache
	ring.util.response ring.middleware.file	ring.adapter.jetty
	[bestinclass wordpress templates comments]))

(def template-updater (agent 0))

(defn update-templates [a]
  (try
   (require 'bestinclass.templates :reload)
   (Thread/sleep 1500)
   (catch Exception e
     (-> e .getMessage println))
   (finally
    (send-off *agent* update-templates))))

;(send-off template-updater update-templates)

(declare wroutes)
(def server (doto (Thread. #(run-jetty #'wroutes {:port 8080})) .start))

(defn static [tplate] (-> tplate response constantly))

(def scripts ["/scripts/jquery.tools.min.js"
	      "/scripts/jquery.fancyzoom.js"
	      "/scripts/menu.js"])

(def posts (reverse (get-posts "wp.xml" bestinclass/post-capture-hook)))

(def wroutes
     (app
      (wrap-file "resources/")
      ["cmt" & url]    {:get  (static (comment-form (apply str (interpose "/" (rest url)))
						    (rand-nth bestinclass.comments/captchas)))
			:post (fn [{body :body}]
				(let [content   (try
						 (apply str (map char (take-while pos? (repeatedly #(.read body)))))
						 (catch Exception e
						   500))]
				  (if (string? content)
				    (receive-comment content)
				    {:body content})))}

      ["pst"]    (static (page (:title posts)
			       (conj scripts "/scripts/post.js")
			       ["/css/main.css" "/css/blog.css" "/css/comment-form.css"]
			       (post (nth posts 1))))

      ["new"]    (static (page "Best In Class - Software Innovator"
			       (conj scripts "/scripts/jquery.slider.js" "/scripts/frontpage.js")
			       ["/css/main.css" "/css/scrollable.css"]
			       (frontpage)))
      ["svc"]    (static (page "Best In Class - Software Innovator" scripts
			       ["/css/main.css"]
			       (services)))
      ["pvc"]    (static (page "Best In Class - Software Innovator" scripts
			       ["/css/main.css"]
			       (produkter)))
      ["blg"]    (static (page "Best In Class - Software Innovator" (conj scripts "/scripts/jquery.pajinate.js")
			       ["/css/main.css" "/css/blog.css" "/css/pagination.css"]
			       (blog posts)))
      ["ktc"]    (static (page "Best In Class - Software Innovator" scripts
			       ["/css/main.css"]
			       (kontakt)))))