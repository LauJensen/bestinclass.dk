(ns bestinclass.shared)

(def static-root "/srv/http/nginx/bestinclass.dk/")

(defn in-tomcat [file]
  (str static-root file))