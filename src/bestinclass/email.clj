(ns bestinclass.email
  (:import (java.util           Properties)
	   (javax.mail          Session Transport Message$RecipientType
				URLName)
	   (javax.mail.internet MimeMessage InternetAddress)))

(defn send-mail
  [recipient subject message]
  (let [props       (doto (Properties.)
		      (.setProperty "mail.host"               "localhost")
		      (.setProperty "mail.user"               "")
		      (.setProperty "mail.password"           "")
		      (.setProperty "mail.transport.protocol" "smtp"))
	msession     (Session/getDefaultInstance props nil)
	mimemsg      (MimeMessage. msession)]
      (doto mimemsg
	(.setSubject   subject)
	(.setFrom      (InternetAddress. "admin@bestinclass.dk"))
	(.setContent   message "text/plain")
	(.addRecipient Message$RecipientType/TO
		       (InternetAddress. recipient)))
      (doto (.getTransport msession)
	.connect
	(.sendMessage mimemsg
		      (.getRecipients mimemsg
				      Message$RecipientType/TO))
	.close)))
