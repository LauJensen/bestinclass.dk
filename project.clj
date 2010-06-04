(defproject bestinclass "1.0.0-SNAPSHOT"
  :description      "Best In Class CMS"
  :repositories     [["maven2-repository.dev.java.net"
		      "http://download.java.net/maven/2/"]]
  :dependencies     [[org.clojure/clojure         "1.2.0-master-SNAPSHOT"]
		     [org.clojure/clojure-contrib "1.2.0-SNAPSHOT"]
		     [enlive                      "1.0.0-SNAPSHOT"]
		     [net.cgrand/moustache        "1.0.0-SNAPSHOT"]
		     [ring/ring-jetty-adapter     "0.2.0"]
		     [javax.mail/mail "1.4.2"]]
  :dev-dependencies [[leiningen/lein-swank        "1.2.0-SNAPSHOT"]])