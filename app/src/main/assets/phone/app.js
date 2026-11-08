/* SPDX-License-Identifier: GPL-3.0-or-later */
const form=document.querySelector('#play-form');
const button=document.querySelector('#go');
const status=document.querySelector('#status');
form.addEventListener('submit',async event=>{
 event.preventDefault();
 const magnet=document.querySelector('#magnet').value.trim();
 if(!magnet.toLowerCase().startsWith('magnet:?')){status.textContent='Paste a magnet link first.';return;}
 button.disabled=true;status.textContent='Sending…';
 const controller=new AbortController();const timeout=setTimeout(()=>controller.abort(),10000);
 try{
  const response=await fetch('play',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded','X-TV-Request':'1'},body:new URLSearchParams({magnet}),signal:controller.signal});
  status.textContent=await response.text();
 }catch(error){status.textContent='Could not reach the TV. Keep Torrent TV open and check that both devices are on the same network.';}
 finally{clearTimeout(timeout);button.disabled=false;}
});
