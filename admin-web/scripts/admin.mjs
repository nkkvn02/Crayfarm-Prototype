import {createRequire} from 'node:module';
const require=createRequire(new URL('../functions/package.json',import.meta.url));
const {initializeApp}=require('firebase-admin/app');
const {getAuth}=require('firebase-admin/auth');
const {getDatabase}=require('firebase-admin/database');
export function localAdmin(){
 process.env.FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:9099';
 process.env.FIREBASE_DATABASE_EMULATOR_HOST='127.0.0.1:9000';
 initializeApp({projectId:'demo-crayfarm',databaseURL:'https://demo-crayfarm-default-rtdb.firebaseio.com'});
 return {auth:getAuth(),db:getDatabase()};
}
