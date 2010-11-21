(defproject bestinclass "1.0.0-SNAPSHOT"
  :description      "Best In Class CMS"
  :main             bestinclass.core
  :namespaces       [bestinclass.admin]
  :repositories     [["maven2-repository.dev.java.net"
		      "http://download.java.net/maven/2/"]]
  :dependencies     [[org.clojure/clojure         "1.2.0-master-SNAPSHOT"]
		     [org.clojure/clojure-contrib "1.2.0-SNAPSHOT"]
		     [enlive                      "1.0.0-SNAPSHOT"]
		     [net.cgrand/moustache        "1.0.0-SNAPSHOT"]
		     [ring/ring-jetty-adapter     "0.2.0"]
                     [log4j                       "1.2.15"
                      :exclusions [javax.mail/mail
                                   javax.jms/jms
                                   com.sun.jdmk/jmxtools
                                   com.sun.jmx/jmxri]]
		     [javax.mail/mail             "1.4.2"]]
  :dev-dependencies [[swank-clojure               "1.3.0-SNAPSHOT"]]
  :jar-files        [["resources" ""]]
  :war-files        [["resources" ""]])