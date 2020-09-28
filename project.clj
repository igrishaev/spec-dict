(defproject spec-dict "0.2.2-SNAPSHOT"

  :description "Better s/keys spec for Clojure"

  :url "https://github.com/igrishaev/spec-dict"

  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]]

  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :release-tasks
  [["test"]
   ["vcs" "assert-committed"]
   ["change" "version" "leiningen.release/bump-version" "release"]
   ["vcs" "commit"]
   ["vcs" "tag"]
   ["deploy" "clojars"]
   ["change" "version" "leiningen.release/bump-version"]
   ["vcs" "commit"]
   ["vcs" "push"]]

  :profiles {:dev {:source-paths ["test"]
                   :dependencies [[org.clojure/clojure "1.10.0"]
                                  [org.clojure/test.check "1.0.0"]]}})
