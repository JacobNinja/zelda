(ns zelda.core
  (:require [cljs.core.async :as async 
             :refer [<! >! chan timeout alts!]]
            [clojure.browser.event :as event]
            [goog.events.KeyHandler]
            [goog.events.KeyCodes :as key-codes]
            [zelda.window]
            [clojure.set :refer [intersection]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [zelda.macros :refer [defhandler]]))

(def keyboard (goog.events.KeyHandler. js/document))
(def keyboard-chan (chan (async/sliding-buffer 1)))
(def valid-keys {key-codes/LEFT :left
                 key-codes/RIGHT :right
                 key-codes/UP :up
                 key-codes/DOWN :down
                 key-codes/SPACE :space})
(def opposite-direction {:left :right
                         :right :left
                         :up :down
                         :down :up})

(defn- keyboard-loop []
  (event/listen keyboard 
                "key"
                (fn [k]
                  (when-let [key (valid-keys (.-keyCode k))]
                    (go (>! keyboard-chan key))))))

(defn- in-bounds? [[width height] [x y]]
  (and (>= x 0) (>= y 0) (< x width) (< y height)))

(defn- collides-with-obstacles? [obstacles coord]
  (some #(= % coord) obstacles))

(defn- adjust-position [[x y] direction]
  (condp = direction
    :right [(inc x) y]
    :left [(dec x) y]
    :up [x (dec y)]
    :down [x (inc y)]
    nil))

(defn- adjust-key [env keyboard-check]
  (assoc env :key keyboard-check))

(defhandler adjust-player [player key dimensions obstacles]
  (let [next-player (.tick player key)]
    (when (and (in-bounds? dimensions (.-coord next-player))
               (not (collides-with-obstacles? obstacles (.-coord next-player))))
      {:player next-player})))

(defhandler strike [key player]
  {:strike (when (= key :space)
             (adjust-position (.-coord player) (.-direction player)))})

(defhandler player-collision-check [enemies player]
  (if-let [player-collisions ((set enemies) (.-coord player))]
    (let [next-player (-> player
                           .tickBackwards
                           (.hit 1))]
      {:player next-player
       :flash (.-coord next-player)})
    {:flash nil}))

(defhandler game-over [player]
  (if (zero? (.-hp player))
    {:game-over "You died!"}))

(defhandler strike-collision-check [enemies strike]
  (let [swing-collision (intersection (set enemies) #{strike})]
    (if-not (empty? swing-collision)
      {:enemies (remove swing-collision enemies)
       :enemy-flash swing-collision}
      {:enemy-flash nil})))

(deftype Player [coord hp direction]
  Object
  (tick [this new-direction]
    (if-let [new-coord (adjust-position coord new-direction)]
      (Player. new-coord hp new-direction)
      this))
  (tickBackwards [this]
    (if-let [back-coord (adjust-position coord (opposite-direction direction))]
      (Player. back-coord hp direction)
      this))
  (hit [this amount]
    (Player. coord (- hp amount) direction)))

(defn- init-env [env]
  (merge env 
         {:player (Player. [5 5] 3 :right)
          :inventory [:sword]
          :obstacles [[8 8]]
          :enemies [[12 5]]}))

(defn- game-loop [draw initial-env]
  (go
   (loop [env (init-env initial-env)]
     (>! draw env)
     (let [[keyboard-check _] (alts! [keyboard-chan (timeout 1)])
           next-env (-> env
                        (adjust-key keyboard-check)
                        adjust-player
                        strike
                        strike-collision-check
                        player-collision-check
                        game-over)]
       (if-not (env :game-over)
         (recur (or (<! (timeout 200)) next-env))
         (js/alert (str "Game over!" \newline (env :game-over))))))))

(defn ^:export init []
  (let [draw-chan (chan)
        env (zelda.window/init draw-chan)]
    (keyboard-loop)
    (game-loop draw-chan env)))

