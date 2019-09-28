(ns app.client
  (:require
    [app.client-app :refer [APP]]
    [app.routing :as routing]
    [app.ui.dynamic-menu :as dynamic-menu]
    [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :refer [ui-dropdown]]
    [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown-menu :refer [ui-dropdown-menu]]
    [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown-item :refer [ui-dropdown-item]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li button h3 label a input table tr td th thead tbody tfoot]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [app.model.session :as session :refer [CurrentUser ui-current-user]]
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [app.routing :as r]))

(defsc LoginForm [this {:ui/keys [email password error? busy?] :as props}]
  {:query         [:ui/email :ui/password :ui/error? :ui/busy?]
   :ident         (fn [] [:component/id :login])
   :route-segment ["login"]
   :initial-state {:ui/email    "foo@bar.com"
                   :ui/error?   false
                   :ui/busy?    false
                   :ui/password "letmein"}}
  (div :.ui.container.segment
    (dom/div :.ui.form {:classes [(when error? "error")]}
      (div :.field
        (label "Username")
        (input {:value    email
                :disabled busy?
                :onChange #(m/set-string! this :ui/email :event %)}))
      (div :.field
        (label "Password")
        (input {:type      "password"
                :value     password
                :disabled  busy?
                :onKeyDown (fn [evt]
                             (when (evt/enter-key? evt)
                               (comp/transact! this [(session/login {:user/email    email
                                                                     :user/password password})])))
                :onChange  #(m/set-string! this :ui/password :event %)}))
      (div :.ui.error.message
        (div :.content
          "Invalid Credentials"))
      (button :.ui.primary.button
        {:classes [(when busy? "loading")]
         :onClick #(comp/transact! this [(session/login {:user/email    email
                                                         :user/password password})])}
        "Login"))))

(defsc Home [this props]
  {:query         [:pretend-data]
   :ident         (fn [] [:component/id :home])
   :route-segment ["home"]
   :initial-state {}}
  (dom/div :.ui.container.segment
    (h3 "Home Screen")))

(defsc Settings [this props]
  {:query                [:pretend-data]
   :ident                (fn [] [:component/id :settings])
   :route-segment        ["settings"]
   :componentDidMount    (fn [this]
                           (dynamic-menu/set-menu! this (dynamic-menu/menu
                                                          (dynamic-menu/link "Other" `other))))
   :componentWillUnmount (fn [this] (dynamic-menu/clear-menu! this))
   :initial-state        {}}
  (dom/div :.ui.container.segment
    (h3 "Settings Screen")))

(defrouter MainRouter [_ _] {:router-targets [LoginForm Home Settings]})

(def ui-main-router (comp/factory MainRouter))

(defsc Root [_ {:root/keys    [ready? router dynamic-menu]
                :session/keys [current-user]}]
  {:query         [:root/ready? {:root/router (comp/get-query MainRouter)}
                   {:root/dynamic-menu (comp/get-query dynamic-menu/DynamicMenu)}
                   {:session/current-user (comp/get-query CurrentUser)}]
   :initial-state (fn [_]
                    {:root/router       (comp/get-initial-state MainRouter)
                     :root/dynamic-menu (dynamic-menu/menu)})}
  (let [logged-in? (:user/valid? current-user)]
    (div
      (div :.ui.top.fixed.menu
        (div :.item
          (div :.content "My Cool App"))
        (when logged-in?
          (comp/fragment
            (div :.item
              (div :.content (a {:href "/home"} "Home")))
            (div :.item
              (div :.content (a {:href "/settings"} "Settings")))
            (dynamic-menu/ui-dynamic-menu dynamic-menu)))
        (div :.right.floated.item
          (ui-current-user current-user)))
      (when ready?
        (div :.ui.grid {:style {:marginTop "4em"}}
          (ui-main-router router))))))

(defmutation finish-login [_]
  (action [{:keys [app state]}]
    (let [logged-in? (get-in @state [:session/current-user :user/valid?])]
      (when-not logged-in?
        (routing/route-to! "/login"))
      (swap! state assoc :root/ready? true))))

(defn refresh []
  (app/mount! APP Root "app"))

(defn ^:export start []
  (app/mount! APP Root "app")
  (routing/start!)
  (df/load! APP :session/current-user CurrentUser {:post-mutation `finish-login}))
