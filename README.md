# D'oh!

D'oh! is a simple, minimal library for handling errors in Clojure. Its only dependency is Clojure 1.9.

## Usage

![](https://clojars.org/doh/latest-version.svg)

Often, I find myself writing a function that is likely to fail. I'd like to include error handling, but I haven't exactly thought out how I'd like to do that yet.

    (defn get-dog
      [name]
      (let [req (http/get (str "/dogs/" name))]
        (if (get-in req [:body :success])
          (:body req)
          ;; TODO: handle error!
          )))

D'oh! uses clojure.spec to let you put in a generic error handler and implement it later. The handler is guaranteed to either return valid data or nil.
    
    (require '[doh.core :refer [handle-error]])
    (require '[clojure.spec :as s])
    (s/def ::dog (s/keys :req-un [::name ::breed ::age]))
  
    (defn get-dog
      [name]
      (let [url (str "/dogs/" name)
            res (http/get url)]
        (if (get-in res [:body :success])
          (:body res)
          (handle-error ::failed-request {:url url :res res)))

    (def-err ::failed-request
      [{:keys [url res]}]
      :retry (http/get url) ;; try again--you could do this in a loop as well
      :ret-spec ::dog ;; only return the result if it fits the spec
      :on-fail (log (format "Failed Requst: %s %s" url (:status res))))
      
    (let [dog (or (get-dog "Avon_Barksdale")
                  (throw (ex-info "Could not get dog" {...})))]
      ;; will always throw unless you have a valid dog
      ;; alternatively, you could throw an exception from the handler
      (do-stuff dog))
      
This is working from inside a function. If you'd rather handle errors outside of a function, the ```handle-exception``` macro will wrap your code in a ```try``` and inject your error handler as necessary:

    (require '[doh.core :refer [handle-exception]])
    (def n nil)
    (handle-exception {NullPointerException ::null-pointer
                       Throwable ::other}
                      {:n n}
                      (do-stuff (inc n)))
                      
    (def-err ::null-pointer
      [{n :n}]
      ::retry (do-stuff (inc (or n 0)))
      ::ret-spec (s/spec number?)
      ;; feel free to leave out unneeded args
      ;; however, if you leave out ::ret-spec, (s/spec any?) will be substituted
      )
    
    (def-err ::other
      [{e :doh/exception}] ;; the exception is automatically passed into the context map
      ::on-fail (println "Unexpected exception" (Throwable->map e)))
      
```def-err``` and ```handle-exception``` are spec'd and should be easy to use. I hope you find this library useful. Suggestions and contributions are welcome.

## License

Copyright © 2016 Gary Nalven

Distributed under the Eclipse Public License version 1.0
