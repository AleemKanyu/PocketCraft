import { initializeApp } from 'firebase/app';
import { getAuth, GoogleAuthProvider, signInWithPopup, signOut } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';

const firebaseConfig = {
  apiKey: "AIzaSyAmJgiyi9Ri3LiFuCIhFt82pPOB1A3q044",
  authDomain: "pocketcraft-9f9ca.firebaseapp.com",
  projectId: "pocketcraft-9f9ca",
  storageBucket: "pocketcraft-9f9ca.appspot.com",
  messagingSenderId: "52965868712",
  appId: "1:52965868712:web:4ee0f09a562ef6d85e6e9d"
};

const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const googleProvider = new GoogleAuthProvider();
export const db = getFirestore(app);

export { signInWithPopup, signOut };
