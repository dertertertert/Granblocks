/* Granblox - login compartilhado (Firebase Authentication + Firestore)
   index.html precisa ter:
     <div id="loginPanel"></div>
     <div id="peoplePanel"></div>
     <script type="module" src="login.js"></script>
   Arquivos na mesma pasta: login.js, firebase-config.js
*/
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import {
  getAuth, onAuthStateChanged, createUserWithEmailAndPassword,
  signInWithEmailAndPassword, signOut, updateProfile
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import {
  getFirestore, doc, getDoc, setDoc, serverTimestamp,
  collection, query, orderBy, limit, getDocs
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";
import { firebaseConfig } from "./firebase-config.js";

const SITE_NAME = "Granblox";
const AVATAR = "avatar.png";            // troque pela sua imagem
const EMAIL_DOMAIN = "@jadecove.example"; // e-mail falso so para o Firebase aceitar nomes de usuario

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

// ---------- estilos ----------
const css = [
  "button, input { font-family: inherit; }",
  ".login label { display: block; margin: 3px 0 1px 0; font-size: 10px; }",
  ".login input[type=text], .login input[type=password] { width: 100%; box-sizing: border-box; font-size: 11px; padding: 2px; border: 1px solid #8e9ccf; }",
  ".login .btns { margin-top: 6px; }",
  ".login button { font-size: 11px; border: 1px solid #6b78b0; background: #e4e9fa; padding: 1px 8px; cursor: pointer; }",
  ".login button:hover { background: #fff; }",
  ".login button:disabled { opacity: .5; cursor: default; }",
  ".msg { margin-top: 5px; font-size: 10px; min-height: 12px; }",
  ".msg.err { color: #b00000; }",
  ".msg.ok { color: #1f7a3a; }",
  ".small { font-size: 10px; }",
  ".hidden { display: none; }",
  ".people { width: 100%; border-collapse: collapse; }",
  ".people td { text-align: center; width: 50%; padding: 4px 0; font-size: 10px; word-break: break-all; }",
  "img.avatar { display: block; width: 40px; height: auto; margin: 0 auto 2px auto; }"
].join("\n");
const st = document.createElement("style");
st.textContent = css;
document.head.appendChild(st);

// ---------- estrutura ----------
const loginMount = document.getElementById("loginPanel");
const peopleMount = document.getElementById("peoplePanel");
if (loginMount) {
  loginMount.innerHTML = `
<div class="panel login">
  <div class="ph" id="loginTitle">Log In</div>
  <div class="pb">
    <div id="viewOut">
      <form id="loginForm" autocomplete="off">
        <label for="lUser">Username:</label>
        <input type="text" id="lUser" maxlength="20">
        <label for="lPass">Password:</label>
        <input type="password" id="lPass" maxlength="40">
        <div class="btns"><button type="submit">Log In</button> <button type="button" id="showSignup">Sign Up</button></div>
      </form>
      <form id="signupForm" class="hidden" autocomplete="off">
        <label for="sUser">Choose a username:</label>
        <input type="text" id="sUser" maxlength="20">
        <label for="sPass">Choose a password:</label>
        <input type="password" id="sPass" maxlength="40">
        <label for="sPass2">Confirm password:</label>
        <input type="password" id="sPass2" maxlength="40">
        <div class="btns"><button type="submit">Sign Up</button> <button type="button" id="showLogin">Back</button></div>
      </form>
    </div>
    <div id="viewIn" class="hidden">
      <p><b>Welcome back, <span id="whoName"></span>!</b></p>
      <img class="avatar" src="${AVATAR}" alt="">
      <p class="small">You are logged in.</p>
      <div class="btns"><button type="button" id="logoutBtn">Log Out</button></div>
    </div>
    <div class="msg" id="msg"></div>
    <p class="small" style="margin-top:6px;">Do not use a password that you use on other sites.</p>
  </div>
</div>`;
}
if (peopleMount) {
  peopleMount.innerHTML = `
<div class="panel">
  <div class="ph">Newest People</div>
  <div class="pb">
    <table class="people" id="peopleTable"></table>
    <div class="small" style="text-align:right;"><a href="browse.html">Browse All &gt;&gt;</a></div>
  </div>
</div>`;
}

const $ = (id) => document.getElementById(id);

if (loginMount) {
  const loginForm = $("loginForm"), signupForm = $("signupForm");
  const viewOut = $("viewOut"), viewIn = $("viewIn"), msg = $("msg");
  let busy = false;

  const say = (text, type) => { msg.className = "msg " + (type || ""); msg.textContent = text || ""; };
  const validName = (n) => /^[A-Za-z0-9_]{3,20}$/.test(n);
  const emailOf = (name) => name.toLowerCase() + EMAIL_DOMAIN;
  const setBusy = (b) => {
    busy = b;
    document.querySelectorAll("#loginPanel button").forEach((el) => { el.disabled = b; });
  };

  function errorText(err) {
    switch (err && err.code) {
      case "auth/email-already-in-use": return "That username is already taken.";
      case "auth/invalid-credential":
      case "auth/wrong-password":
      case "auth/user-not-found":
      case "auth/invalid-login-credentials": return "Wrong username or password.";
      case "auth/weak-password": return "Password must have at least 6 characters.";
      case "auth/too-many-requests": return "Too many attempts. Try again later.";
      case "auth/network-request-failed": return "Network error. Check your connection.";
      case "auth/api-key-not-valid":
      case "auth/invalid-api-key": return "Site setup error (invalid API key).";
      default: return "Something went wrong. (" + ((err && err.code) || "unknown") + ")";
    }
  }

  function render() {
    const user = auth.currentUser;
    if (user) {
      viewOut.classList.add("hidden");
      viewIn.classList.remove("hidden");
      $("whoName").textContent = user.displayName || (user.email || "").split("@")[0];
      $("loginTitle").textContent = "My " + SITE_NAME;
    } else {
      viewIn.classList.add("hidden");
      viewOut.classList.remove("hidden");
      $("loginTitle").textContent = "Log In";
    }
  }

  async function renderPeople() {
    const table = $("peopleTable");
    if (!table) return;
    let names = [];
    try {
      const snap = await getDocs(query(collection(db, "users"), orderBy("created", "desc"), limit(8)));
      names = snap.docs.map((d) => d.data().name).filter(Boolean);
    } catch (e) {
      table.innerHTML = "";
      const r = table.insertRow(); const c = r.insertCell();
      c.colSpan = 2; c.textContent = "Could not load the list.";
      return;
    }
    table.innerHTML = "";
    if (names.length === 0) {
      const r = table.insertRow(); const c = r.insertCell();
      c.colSpan = 2; c.textContent = "No one has joined yet. Be the first!";
      return;
    }
    for (let i = 0; i < names.length; i += 2) {
      const row = table.insertRow();
      for (let j = i; j < i + 2; j++) {
        const cell = row.insertCell();
        if (names[j]) {
          cell.innerHTML = '<img class="avatar" src="' + AVATAR + '" alt="">';
          const span = document.createElement("span");
          span.textContent = names[j];
          cell.appendChild(span);
        }
      }
    }
  }

  // se a conta existir mas o perfil nao (falha no meio do cadastro), cria o perfil
  async function ensureProfile(user) {
    try {
      const ref = doc(db, "users", user.uid);
      const snap = await getDoc(ref);
      if (!snap.exists()) {
        const name = user.displayName || (user.email || "").split("@")[0];
        if (validName(name)) await setDoc(ref, { name: name, created: serverTimestamp() });
      }
    } catch (e) { /* ignora */ }
  }

  $("showSignup").onclick = () => {
    loginForm.classList.add("hidden"); signupForm.classList.remove("hidden");
    $("loginTitle").textContent = "Sign Up"; say("");
  };
  $("showLogin").onclick = () => {
    signupForm.classList.add("hidden"); loginForm.classList.remove("hidden");
    $("loginTitle").textContent = "Log In"; say("");
  };

  signupForm.onsubmit = async (e) => {
    e.preventDefault();
    if (busy) return;
    const u = $("sUser").value.trim(), p = $("sPass").value, p2 = $("sPass2").value;
    if (!validName(u)) return say("Username: 3 to 20 letters, numbers or _.", "err");
    if (p.length < 6) return say("Password must have at least 6 characters.", "err");
    if (p !== p2) return say("Passwords do not match.", "err");
    setBusy(true); say("Creating account...");
    try {
      const cred = await createUserWithEmailAndPassword(auth, emailOf(u), p);
      try { await updateProfile(cred.user, { displayName: u }); } catch (e2) {}
      try {
        await setDoc(doc(db, "users", cred.user.uid), { name: u, created: serverTimestamp() });
      } catch (e3) { /* o perfil e recriado no proximo login */ }
      signupForm.reset();
      signupForm.classList.add("hidden");
      loginForm.classList.remove("hidden");
      say("Account created! Welcome, " + u + ".", "ok");
      render();
      renderPeople();
    } catch (err) {
      say(errorText(err), "err");
    }
    setBusy(false);
  };

  loginForm.onsubmit = async (e) => {
    e.preventDefault();
    if (busy) return;
    const u = $("lUser").value.trim(), p = $("lPass").value;
    if (!validName(u) || !p) return say("Wrong username or password.", "err");
    setBusy(true); say("Logging in...");
    try {
      const cred = await signInWithEmailAndPassword(auth, emailOf(u), p);
      loginForm.reset();
      say("");
      await ensureProfile(cred.user);
      render();
      renderPeople();
    } catch (err) {
      say(errorText(err), "err");
    }
    setBusy(false);
  };

  $("logoutBtn").onclick = async () => {
    try { await signOut(auth); } catch (e) {}
    say("You have logged out.", "ok");
    render();
  };

  onAuthStateChanged(auth, render);
  render();
}

renderPeople();
