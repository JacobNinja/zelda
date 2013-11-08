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

(defhandler strike [key player inventory]
  {:strike (when (and (= key :space)
                      (inventory :sword))
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
  (when (zero? (.-hp player))
    {:game-over "You died!"}))

(defhandler strike-collision-check [enemies strike]
  (let [swing-collision (intersection (set enemies) #{strike})]
    (if-not (empty? swing-collision)
      {:enemies (remove swing-collision enemies)
       :enemy-flash swing-collision}
      {:enemy-flash nil})))

(defhandler pickup [pickup-items player inventory]
  (when-let [item-collisions
             (map first (filter (fn [[_ coord]] 
                                  (= coord (.-coord player))) pickup-items))]
    {:inventory (into inventory item-collisions)
     :pickup-items (apply dissoc (cons pickup-items item-collisions))}))

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
    (Player. coord (- hp amount) direction))
  (toString [this]
    (str coord)))

(defn- init-env [env]
  (merge env 
         {:player (Player. [5 5] 3 :right)
          :inventory #{}
          :obstacles #{[0 7] [1 8] [2 9] [3 10] [4 10] [0 8] [1 7] [2 10] [3 9] [5 10] [0 9] [1 10] [6 10] [0 10] [1 9] [7 10] [7 9] [6 9] [5 9] [4 9] [13 9] [14 10] [12 9] [15 10] [12 10] [14 8] [15 9] [13 10] [14 9] [15 8] [9 10] [12 4] [14 6] [15 7] [8 9] [9 9] [13 4] [14 7] [15 6] [8 10] [10 9] [11 10] [14 4] [10 10] [11 9] [15 4] [11 0] [12 0] [13 1] [14 2] [15 3] [10 0] [12 1] [13 0] [14 3] [15 2] [12 2] [13 3] [14 0] [15 1] [9 0] [12 3] [13 2] [14 1] [15 0] [10 3] [11 4] [9 2] [10 4] [11 3] [9 1] [10 1] [11 2] [9 4] [10 2] [11 1] [9 3] [1 0] [2 1] [3 2] [0 0] [2 2] [3 1] [0 1] [1 2] [3 0] [0 2] [1 1] [2 0] [0 3] [1 4] [6 0] [0 4] [1 3] [6 1] [1 6] [2 3] [4 0] [5 1] [0 6] [4 1] [5 0]}}))

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
                        pickup
                        game-over)]
       (if-not (env :game-over)
         (recur (or (<! (timeout 200)) next-env))
         (js/alert (str "Game over!" \newline (env :game-over))))))))

(defn ^:export init []
  (let [draw-chan (chan)
        env (zelda.window/init draw-chan)]
    (keyboard-loop)
    (game-loop draw-chan env)))

