(defproject spec-dict "0.2.0"

  :description "Better s/keys spec for Clojure"

  :url "https://github.com/igrishaev/spec-dict"

  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]]

  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :release-tasks
  [["test"]
   #_["vcs" "assert-committed"]
   ["change" "version" "leiningen.release/bump-version" "release"]
   ["vcs" "commit"]
   ["vcs" "tag"]
   ["deploy"]
   ["change" "version" "leiningen.release/bump-version"]
   ["vcs" "commit"]
   ["vcs" "push"]]

  :profiles {:dev {:source-paths ["test"]
                   :dependencies [[org.clojure/clojure "1.10.0"]
                                  [org.clojure/test.check "1.0.0"]]}})
