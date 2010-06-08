(ns bestinclass.templates
  (:use [net.cgrand.enlive-html :exclude [flatten]]))

(def thumb-prefix "http://www.bestinclass.dk/wp-content/uploads/avatars")

(defsnippet frontpage "index.html"     [:body :> any-node] [])
(defsnippet services  "services.html"  [:body :> any-node] [])
(defsnippet produkter "produkter.html" [:body :> any-node] [])
(defsnippet kontakt   "kontakt.html"   [:body :> any-node] [])

; Blog: Index
(defsnippet blog "blog.html" [:body :> any-node]
  [posts]
  [:ul.content :li]    (clone-for [{:keys [title body thumb excerpt link]} posts]
				  [:a.title-link]     (do-> (set-attr :href link)
							    (content title))
				  [:a.thumb-link]     (set-attr :href link)
				  [:div.link-float-right
				   :a.perma-link]      (set-attr :href link)
				  [:img.avatar]       (set-attr :src (str thumb-prefix thumb))
				  [:div.excerpt]      (content (html-snippet excerpt))))

; Blog: Single post
(defsnippet post "blogpost.html" [:body :> any-node]
  [{:keys [title body thumb date comments link]}]
  [:h1#title]          (content title)
  [:img#thumb]         (set-attr :src (str thumb-prefix thumb))
  [:div#pubdate]       (content date)
  [:a#addcomment]      (set-attr :href (str "/cmt/url=" link))
  [:div#post]          (append (html-snippet body))
  [:div.comment]       (clone-for [{:keys [author email date comment]} comments]
				  [:div.author]       (content author)
				  [:div.date]         (content date)
				  [:div.message :pre] (content comment)))

; Raw template for all pages, include header/footer
(deftemplate page "template.html" [title scripts styles body]
  [:title]             (content title)
  [:div#pages :a]      (clone-for [[href src] [["/index.html"     "/images/forside-lnk.png"]
					       ["/services.html"  "/images/services-lnk.png"]
					       ["/produkter.html" "/images/produkter-lnk.png"]
					       ["/blog.html"      "/images/blog-lnk.png"]
					       ["/kontakt.html"   "/images/kontakt-lnk.png"]]]
				  this-node (set-attr :href href)
				  [:img]    (set-attr :src src))
  [:script.header]     (clone-for [src scripts] (set-attr :src src))
  [[:link (attr= :rel "stylesheet")]]
                       (clone-for [href styles] (set-attr :href href))
  [:div#content]       (substitute body))

; Feeds
(deftemplate atom-feed "atom.xml" [timestamp posts]
  [:updated]           (content timestamp)
  [:feed :entry]       (clone-for [{:keys [title link id updated excerpt body]} posts]
				  [:title]           (content title)
				  [:link]            (set-attr :href link)
				  [:id]              (content id)
				  [:updated]         (content updated)
				  [:summary]         (content excerpt)
				  [:content]         (content body))
  [:feed]              (set-attr :xmlns "http://www.w3.org/2005/Atom"))

; Admin helpers

(defsnippet editor "editor.html" [:body :> any-node]  [draft]
  [:textarea#editor] (content (html-snippet draft)))

(defsnippet teaser "teaser.html" [:body :> any-node]
  [title link thumb excerpt]
  [:a.title-link]                       (do-> (set-attr :href link)
					      (content title))
  [:a.thumb-link]                       (set-attr :href link)
  [:div.link-float-right :a.perma-link] (set-attr :href link)
  [:img.avatar]                         (set-attr :src (str thumb-prefix thumb))
  [:div.excerpt]                        (content (html-snippet excerpt)))

(defsnippet a-comment "comment.html" [:body :> any-node]
  [class author date message]
  [:div.ctype]        (set-attr :class class)
  [:div.author]       (content author)
  [:div.date]         (content date)
  [:div.message :pre] (content message))

; Admin Interface

(deftemplate admin-page "admin.html" [article avatars comments today week month referers]
  [:div#article] (content
		  (html-snippet
		   (apply str
			  (emit*
			   (post {:title "New Title"
				  :body article
				  :thumb "/fight.png"
				  :date "10-10-10 10:10:10"
				  :comments []
				  :link "/"})))))
  [:span#today]  (content "Today: "      today)
  [:span#week]   (content "This week: "  week)
  [:span#month]  (content "This month: " month)
  [:tr.refrow]   (clone-for [[lnk dt] referers]
			    [:td.ref :a] (do-> (content lnk)
					       (set-attr :href lnk))
			    [:td.dt]  (content (.toString dt)))
  [:option.pic]  (clone-for [avatar avatars]
			    this-node (content avatar))
  [:div.comment-wrap]
  (clone-for [{:keys [name email date comment url id ]} comments]
	     [:div.comment :div.author]       (content (str name " (" email ")"))
	     [:div.comment :div.date]         (content date)
	     [:div.comment :div.message :pre] (content comment)
	     [:div.comment :div.url]          (when (string? url)
						(content (subs url (inc (.lastIndexOf url "/")))))
	     [:div.comment :a#dispose]
	     (set-attr :href (str "/dispose/" id))
	     [:div.comment :a#approve]
	     (set-attr :href (str "/approve/" id "/" url))))
