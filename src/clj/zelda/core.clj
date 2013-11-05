(ns zelda.core
  (:use [compojure.core]
        [hiccup.core])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]))

(def page
  (html
   [:head {:title "Zelda"}]
   [:body {:onload "zelda.core.init();"}
    [:div
     [:div
      [:span {:id "hit-points"}
       [:img {:src "images/heart.png"}]]
      "&nbsp;"
      [:span "Inventory: " [:span {:id "inventory"}]]]
     [:canvas#world]
     [:script {:src "js/dev.js"}]]]))

(defroutes app-routes
  (GET "/" [] page)
  (route/resources "/")
  (route/not-found "Not found"))

(def app (handler/site app-routes))

