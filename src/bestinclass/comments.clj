(ns bestinclass.comments
  (:use [net.cgrand.enlive-html :exclude [flatten]]
	net.cgrand.moustache
	clojure.contrib.io
	[bestinclass email wordpress templates])
  (:import [java.io File]
	   [java.text SimpleDateFormat]))

					;:> GLOBALS
(def persister-tron (agent 0))
(def comment-queue (ref []))
(def captchas [{:id 0 :question "(+ (* 2 2) 6) = ?" :answer "10"}
	       {:id 1 :question "(/ 10 2) = ?"      :answer "5"}
	       {:id 2 :question "2 * 8 = ?"         :answer "16"}
	       {:id 3 :question "(+ 1 2 3) = ?"     :answer "6"}])

(deftemplate comment-form "addcomment.html" [url captcha]
  [:input#url]  (set-attr :value url)
  [:input#cid]  (set-attr :value (:id captcha))
  [:p#question] (content (:question captcha)))

					;:> BACKGROUND JOBS

(defn backup-comments [a]
  (doseq [comment (dosync
		   (let [comments @comment-queue]
		     (ref-set comment-queue [])
		     comments))]
    (append-spit "comment-queue" (with-out-str (prn comment))))
  (Thread/sleep 60000)
  (send-off *agent* backup-comments))

					;:> BACKEND FUNCTIONALITY

(defn receive-comment [params]
  (try
   (let [{:keys [url name email captcha cid comment]}
	 (->> (.split (java.net.URLDecoder/decode params) "&")
	      (map #(let [[k v] (.split % "=")] {(keyword k) v}))
	      (into {}))
	 date   (.format (SimpleDateFormat. "yyyy-MM-dd hh:mm:ss") (java.util.Date.))
	 {:keys [answer question]} (nth captchas (Integer/parseInt cid))]
     (if (and (= captcha answer)
	      (.contains url "index.clj")
	      (not (.contains url "..")))
       (dosync
	(alter comment-queue conj
	       {:url     url, :name name, :email email
		:captcha (format "Answered %s to question #%s (%s)" captcha cid question)
		:date    (.toString date)
		:comment comment})
	(do
	  (future (send-mail "lau@bestinclass.dk" "New comment" "Check it out"))
	  {:body "OK"}))
       {:body "NOT OK"}))
   (catch Exception e {:body "NOT OK"})))
