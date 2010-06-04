(ns bestinclass.wordpress
  (:use    [net.cgrand.enlive-html :exclude [flatten]]
	   clojure.contrib.io
	   bestinclass.templates)
  (:import [java.io File]))

;TODO: Force Christophe to give me select1
(defn pick [nodes selector] (-> (select nodes selector) first))

; (knit [:a :b] [2 3]) => {:a 2, :b 3}
(defn knit [names values] (into {} (map #(hash-map %1 %2) names values)))

(defn loot
  " loot takes a chunk of html/xml in an Enlive tree-struct and retrieves
    the result of applying each of the 'selectors' + content. Each result is
    sequentally associated with a name from 'names'

   (loot (html-resource '<h1>Hey</h1>') [:title] :h1)
   >> {:title 'Hey'}"
  [chunk names selectors]
  (knit names (map (fn [selector]
		     (pick chunk (if (coll? selector)
				     selector
				     [selector content])))
			   selectors)))

(defn extract-comments [post]
  (let [comments (select post [[:wp:comment (has [[:wp:comment_approved (pred #(= "1" (text %)))]])
				       (but (has [[:wp:comment_type (pred #(= "pingback" (text %)))]]))]])]
    (sort-by :date compare
	     (for [c comments]
	       (loot c [:author :email :date :comment]
		     [:wp:comment_author :wp:comment_author_email
		      :wp:comment_date :wp:comment_content])))))

(defn get-posts
  " Takes an Wordpress backup file as its first argument and a function of 1-args as its second.

    The wordpress file is parsed for post data and this is return in hash-maps containing keys
    [:title :link :body :thumb]

    Thumb is specific to users of the post-avatar plugin. After the data is retrieved the
    post-capture-hook is applied to each item. Use this to sanitize, modify, etc."
  [file post-capture-hook]
  (let [posts  (-> file xml-resource
		   (select [[:item (has [[:wp:post_type (pred #(= "post" (text %)))]])]]))]
    (map post-capture-hook
	    (for [{i :content} posts]
	      (-> (loot i [:title :link :body :date :thumb]
			[:title :link :content:encoded :wp:post_date
			 [[:wp:postmeta (has [[:wp:meta_key (pred #(= "postuserpic" (text %)))]])]
			  [:wp:meta_value] content]])
		  (assoc :comments (extract-comments i)))))))

(defn convert-posts
  [backup-file hook]
  (let [posts (get-posts backup-file hook)]
    (doseq [{:keys [title link body thumb] :as article} posts
	    :let [file (File. (.substring link 1))]]  ; Skip the /
      (println "Converting: " title)
      (make-parents file)
      (spit file (->> (page (str "Best In Class: " title)
			    ["/scripts/jquery.tools.min.js" "/scripts/menu.js" "/scripts/post.js"]
			    ["/css/main.css" "/css/blog.css" "/css/comment-form.css"]
			    (post article))
		      (apply str))))
    (reverse posts)))



