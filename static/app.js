'use strict';
const $ = id => document.getElementById(id);
const state = {boot:null, session:null, view:'prepare', retake:null, busy:false, stream:null,
  poll:null, pageSignature:'', verifyRevision:null, formDirty:false, fieldsEdited:false, cropPage:null, corners:[],
  pendingUpload:null, qualityBusy:false, previousFrame:null, label:'', labelCount:0,
  stableSince:0, lastCapture:0, lastCapturedFrame:null, autoArmed:true};
const errors = {
  PAIRING_REQUIRED:'Pairing expired. Reconnect with the code on your laptop.',
  PAIRING_CODE_INCORRECT:'That pairing code does not match. Check the laptop.',
  PAIRING_RATE_LIMIT:'Too many pairing attempts. Wait one minute and try again.',
  INVALID_INPUT:'Check the entered values. Custom paper must be 20–2000 mm and larger than its margins.',
  NAME_COLLISION:'That name already exists. Change the filename, approve again, and save.',
  SAVE_FAILED_CHECK_FOLDER_OR_DISK:'Saving failed. Check folder permissions and free disk space, then retry.',
  UNSUPPORTED_OR_INVALID_PHOTO:'This photo could not be opened. Use JPG, PNG, WebP, or supported HEIC/HEIF, under 32 MB.',
  EXPECTED_COUNT_MISMATCH:'Captured and expected counts differ. Add pages or update the expected count before processing.',
  APPROVE_DOCUMENT_FIRST:'Review and approve the document before saving or printing.',
  STATE_CHANGED_REFRESH:'The document changed on another device. Your fields are kept; review the new pages and approve again.',
  PAGE_CHANGED_REFRESH:'That page changed on another device. Refresh it before replacing or correcting it.',
  INVALID_FILENAME:'Use a filename without Windows reserved names or these characters: < > : " / \\ | ? *',
  DESTINATION_NOT_APPROVED:'Select an approved laptop folder. Add another folder from the laptop interface.',
  FOLDER_UNWRITABLE:'The laptop cannot write to that folder. Choose a different destination.',
  FOLDER_MUST_EXIST:'Choose an existing laptop folder.',
  DESTINATION_PROTECTED:'Choose an output folder outside I-SKAN application, certificate and working-data folders.',
  PROCESS_PAGES_FIRST:'Process every page before final verification.',
  PAGE_LIMIT_RETAKE_OR_NEW_SESSION:'Single mode accepts one page. Retake it, or start a Multiple scan.',
  SAVED_SESSION_LOCKED:'This document is already saved. Start a new scan for another document.',
  SESSION_MISSING:'This draft is no longer available. Drafts expire after seven days.',
  OPERATION_FAILED:'The operation failed. Your received captures are kept. Retry or check laptop setup.',
};
function notice(text, retry=false) {
  $('notice').replaceChildren(); $('notice').hidden = !text;
  if(!text) return;
  const span=document.createElement('span'); span.textContent=text; $('notice').append(span);
  if(retry) {const b=button('Retry upload',()=>retryUpload()); $('notice').append(b);}
  $('notice').append(button('Dismiss',()=>notice('')));
}
function button(text, handler, cls='') {const b=document.createElement('button'); b.type='button'; b.textContent=text; b.className=cls; b.onclick=()=>act(handler); return b;}
async function api(path, options={}) {
  const opts={...options, headers:{...(options.headers||{})}};
  if(opts.body && !(opts.body instanceof FormData)) {opts.headers['Content-Type']='application/json'; opts.body=JSON.stringify(opts.body);}
  let response;
  try {response=await fetch('/api'+path,opts);} catch {throw new Error('Laptop connection lost. Received pages are kept. Keep the laptop awake and reconnect.');}
  if(!response.ok) {
    const data=await response.json().catch(()=>({detail:'OPERATION_FAILED'}));
    if(response.status===401) {stopCamera(); $('pair').hidden=false; $('app').hidden=true;}
    const err=new Error(errors[data.detail] || `Unable to complete this step (${data.detail || response.status}).`);
    err.code=data.detail; throw err;
  }
  return response.json();
}
async function act(fn) {try {await fn();} catch(e) {notice(e.message);}}
async function boot() {
  try {state.boot=await api('/bootstrap');}
  catch(e) {
    if(e.code==='PAIRING_REQUIRED') {
      try {await api('/login',{method:'POST',body:{}}); state.boot=await api('/bootstrap');}
      catch {$('pair').hidden=false; $('app').hidden=true; $('connection').textContent='Pairing needed'; return;}
    } else {notice(e.message); return;}
  }
  $('pair').hidden=true; $('app').hidden=false; $('connection').textContent='Laptop connected';
  $('connection').classList.remove('offline');
  fillForm($('prepare-form'),state.boot.settings);
  prepVisibility(); renderDestinations(); renderSidebar();
  $('pairing').hidden=!state.boot.laptop; $('folder-controls').hidden=!state.boot.laptop;
  $('pairing-code').textContent=state.boot.pairing_code||'';
  $('lan-url').textContent=state.boot.public_url||'Local mode. Follow connection help to enable secure phone access.';
  if(state.boot.public_url) {$('qr').src='/api/qr'; $('qr').hidden=false;}
  const sid=localStorage.getItem('iskan-session');
  const draft=state.boot.sessions.find(s=>s.id===sid);
  if(draft) selectSession(draft); else showView('prepare');
  if(!state.poll) state.poll=setInterval(()=>poll(),1500);
}
function showView(view) {
  state.view=view;
  for(const id of ['prepare','session','history']) $(id).hidden=id!==view;
  if(view!=='session') stopCamera();
}
function fillForm(form, config) {
  for(const [key,value] of Object.entries(config)) {
    const inputs=form.querySelectorAll(`[name="${key}"]`);
    inputs.forEach(input=>{if(input.type==='radio') input.checked=input.value===value;
      else if(input.type==='checkbox') input.checked=value; else input.value=value;});
  }
}
function readForm(form, base) {
  const config={...base};
  const numbers=['expected','width','height','margin','strength','watermark_size','watermark_opacity','number_start','quality'];
  for(const input of form.querySelectorAll('[name]')) {
    if(input.type==='radio' && !input.checked) continue;
    config[input.name]=input.type==='checkbox'?input.checked:numbers.includes(input.name)?Number(input.value):input.value;
  }
  if(config.mode==='single') config.expected=1;
  return config;
}
function prepVisibility() {
  const form=$('prepare-form');
  $('expected-wrap').hidden=form.elements.mode.value!=='multiple';
  $('custom-paper').hidden=form.elements.paper.value!=='Custom';
}
function renderSidebar() {
  $('drafts').replaceChildren();
  const sessions=state.boot.sessions;
  $('draft-count').textContent=sessions.length;
  for(const s of sessions) {
    const b=button(s.approved?.title||s.metadata?.title||s.suggestions?.title||(s.settings.mode==='single'?'Single page':'Multiple pages'),()=>selectSession(s),'draft'+(s.id===state.session?.id?' active':''));
    const small=document.createElement('small'); small.textContent=`${s.pages.length} page${s.pages.length===1?'':'s'} · ${s.saved?'Saved':s.scanned_date||'New draft'}`; b.append(small); $('drafts').append(b);
  }
  $('total-scans').textContent=state.boot.stats.scan_count; $('total-pages').textContent=state.boot.stats.page_count;
}
function renderDestinations() {
  const current=$('destination').value||state.boot.settings.destination;
  $('destination').replaceChildren(...state.boot.destinations.map(path=>{const o=document.createElement('option');o.value=path;o.textContent=path;return o;}));
  if(state.boot.destinations.includes(current)) $('destination').value=current;
}
function selectSession(s) {
  stopCamera();state.retake=null;state.session=s;state.pageSignature='';state.verifyRevision=null;state.formDirty=false;state.fieldsEdited=Boolean(s.approved||s.metadata);
  localStorage.setItem('iskan-session',s.id);showView('session');renderSession();renderSidebar();
}
function acceptSession(s) {
  state.session=s; const i=state.boot.sessions.findIndex(x=>x.id===s.id);
  if(i>=0) state.boot.sessions[i]=s; else state.boot.sessions.unshift(s);
  renderSession();renderSidebar();
}
async function poll() {
  if(!state.boot || $('app').hidden || state.busy) return;
  try {
    if(state.session && state.view==='session') {
      const s=await api(`/sessions/${state.session.id}`);
      const before=state.session.revision;state.session=s;
      const i=state.boot.sessions.findIndex(x=>x.id===s.id);if(i>=0) state.boot.sessions[i]=s;
      if(before!==s.revision && state.formDirty) $('approval-state').textContent='Pages changed · review again';
      renderSession();
    }
    // Discover scans created on the other device, without resetting current fields.
    if(!state.lastBoot || Date.now()-state.lastBoot>5000) {
      const b=await api('/bootstrap');state.boot.sessions=b.sessions;state.boot.stats=b.stats;state.boot.destinations=b.destinations;
      state.lastBoot=Date.now();renderSidebar();renderDestinations();
    }
    $('connection').textContent='Laptop connected';$('connection').classList.remove('offline');
  } catch(e) {
    $('connection').textContent='Disconnected · drafts kept';$('connection').classList.add('offline');
    if(e.code==='SESSION_MISSING') {state.session=null;localStorage.removeItem('iskan-session');showView('prepare');notice(e.message);}
  }
}
function asset(p,kind='original') {return `/api/sessions/${state.session.id}/pages/${p.id}/${kind}?r=${p.revision}`;}
function renderSession() {
  const s=state.session;if(!s)return;
  const ready=s.pages.length>0&&s.pages.every(p=>p.status==='ready');
  const saved=Boolean(s.saved);
  $('folder-controls').hidden=!state.boot.laptop||saved;
  $('session-title').textContent=s.approved?.title||'Your document';
  const processing=s.pages.find(p=>['queued','processing'].includes(p.status));
  const index=processing?s.pages.indexOf(processing)+1:0;
  $('session-status').textContent=saved?'Saved on your laptop':processing?`Processing page ${index} of ${s.pages.length} · ${processing.stage}`:ready?'Pages processed. Check the output and approve your title.':'Capture, correct, and arrange your original pages.';
  $('capture-progress').textContent=s.settings.mode==='multiple'?`${s.pages.length} of ${s.settings.expected} captured`:`${s.pages.length} page captured`;
  const multiple=s.settings.mode==='multiple';
  $('expected-inline').hidden=!multiple||saved;$('update-expected').hidden=!multiple||saved;
  if(document.activeElement!==$('expected-input')) $('expected-input').value=s.settings.expected;
  for(const id of ['camera-open','upload','process','settings-button']) $(id).disabled=saved;
  document.querySelector('label[for=upload]').hidden=saved;
  $('camera-open').hidden=saved||(!multiple&&s.pages.length===1&&!state.retake);
  $('process-bar').hidden=saved;
  $('empty-pages').hidden=s.pages.length>0;
  $('count-note').textContent=multiple&&s.pages.length!==s.settings.expected?`${s.settings.expected-s.pages.length>0?s.settings.expected-s.pages.length+' more expected':s.pages.length-s.settings.expected+' extra captured'}. Add pages or explicitly update the count.`:'Processing starts only when you choose Process.';
  $('process').disabled=!s.pages.length||Boolean(processing)||(multiple&&s.pages.length!==s.settings.expected);
  $('process').textContent=ready?'All pages processed':s.pages.some(p=>p.status==='failed')?'Retry unfinished pages':'Process all pages';
  $('print-request').hidden=!s.print_ready||!state.boot.laptop;
  const sig=s.pages.map(p=>`${p.id}:${p.revision}:${p.status}:${p.stage}`).join('|')+saved;
  if(sig!==state.pageSignature) {renderPages();state.pageSignature=sig;}
  $('verify').hidden=!ready;
  if(ready) {
    const selected=Number($('preview-page').value)||0;
    if($('preview-page').options.length!==s.pages.length || state.verifyRevision!==s.revision) {
      $('preview-page').replaceChildren(...s.pages.map((p,i)=>{const o=document.createElement('option');o.value=i;o.textContent=`Page ${i+1}`;return o;}));
      $('preview-page').value=Math.min(selected,s.pages.length-1);
    }
    if(state.verifyRevision!==s.revision) {
      if(!state.fieldsEdited||state.verifyRevision===null) {
        const a=s.approved||s.metadata||s.suggestions;
        $('title').value=a.title||'Untitled scan';$('filename').value=a.filename||'Scan';
        $('document-date').value=a.document_date||'';
        $('destination').value=a.destination||s.settings.destination;
      }
      $('title-note').textContent=s.suggestions.title_note||'Use the title you want to keep.';
      $('date-note').textContent=s.suggestions.date_note||'Document date can remain blank.';
      $('date-candidates').replaceChildren(...(s.suggestions.date_candidates||[]).map(d=>button(d,()=>{$('document-date').value=d;formEdited();})));
      state.verifyRevision=s.revision;updatePreview();
    }
    $('scanned-date').textContent=`Scanned ${s.scanned_date} · Asia/Taipei. This is separate from the document date.`;
    $('approval-state').textContent=saved?'Saved':s.approved?'Approved':'Review before saving';
    $('approve').disabled=saved;$('approve').textContent=saved?'Document saved':s.approved?'Approved · edit to change':'Approve this document';
    for(const id of ['save','print','save-print']) $(id).disabled=!s.approved||state.busy||state.formDirty;
    $('save').disabled=saved||!s.approved||state.busy;$('save-print').disabled=saved||!s.approved||state.busy;
    for(const id of ['title','document-date','filename','destination']) $(id).disabled=saved;
    if(saved) $('save-result').textContent=`Saved: ${s.saved}`;
  }
  $('step-label').textContent=ready?'03 / VERIFY & FINISH':'02 / CAPTURE & REVIEW';
  $('discard').textContent=saved?'Remove working copy (saved output stays)':'Discard working scan';
}
function renderPages() {
  const s=state.session;$('pages').replaceChildren();
  s.pages.forEach((p,i)=>{
    const tile=document.createElement('article');tile.className='page-tile';
    const img=document.createElement('img');img.className='page-image';img.alt=`Original page ${i+1}`;
    img.src=asset(p,p.original_retained?'original':'processed');img.onclick=()=>act(()=>openCrop(p));tile.append(img);
    const info=document.createElement('div');info.className='page-info';
    const title=document.createElement('strong');title.textContent=`PAGE ${String(i+1).padStart(2,'0')}`;
    const note=document.createElement('p');note.textContent=p.status==='captured'?(p.quality.warnings.join(' · ')||'Original ready for review'):p.stage+(p.ocr_warning?' · '+p.ocr_warning:'');
    info.append(title,note);
    if(!s.saved) {
      const buttons=document.createElement('div');buttons.className='page-buttons';
      buttons.append(button('Review / crop',()=>openCrop(p)),button('Retake',()=>retake(p)),button('Remove',()=>removePage(p)));
      if(p.status==='ready')buttons.append(button('Processed',()=>{$('page-preview-image').src=asset(p,'processed');$('page-preview-dialog').showModal();}));
      if(i>0)buttons.append(button('← Earlier',()=>movePage(i,-1)));
      if(i<s.pages.length-1)buttons.append(button('Later →',()=>movePage(i,1)));
      info.append(buttons);
    }
    tile.append(info);$('pages').append(tile);
  });
}
function updatePreview() {
  if(!state.session?.pages.length)return;
  const s=state.session,i=Number($('preview-page').value)||0,p=s.pages[i];
  const original=$('compare-original').checked&&p.original_retained;
  if($('compare-original').checked&&!p.original_retained) {$('compare-original').checked=false;notice('Originals were removed after saving, as selected in retention settings.');}
  $('final-preview').src=original?asset(p):`/api/sessions/${s.id}/preview/${i}?r=${s.revision}`;
  $('final-preview').alt=original?`Original page ${i+1}`:`Export appearance for page ${i+1}`;
}
async function updateSettings(config) {
  const s=await api(`/sessions/${state.session.id}/settings?revision=${state.session.revision}`,{method:'PUT',body:config});
  acceptSession(s);state.boot.settings=config;fillForm($('prepare-form'),config);prepVisibility();
}
async function retake(p) {
  state.retake={id:p.id,revision:p.revision};
  notice(`Retaking page ${state.session.pages.indexOf(p)+1}. Capture or upload one photo to replace it in place.`);
  $('camera-open').hidden=false;$('upload').multiple=false;
  await openCamera();
}
async function removePage(p) {
  if(!confirm('Remove this page from the document?'))return;
  acceptSession(await api(`/sessions/${state.session.id}/pages/${p.id}?revision=${state.session.revision}`,{method:'DELETE'}));
}
async function movePage(i,direction) {
  const ids=state.session.pages.map(p=>p.id);[ids[i],ids[i+direction]]=[ids[i+direction],ids[i]];
  acceptSession(await api(`/sessions/${state.session.id}/order`,{method:'PUT',body:{revision:state.session.revision,pages:ids}}));
}
async function uploadBlob(blob) {
  const pending={blob,sid:state.session.id,capture_id:crypto.randomUUID(),replace:state.retake?.id||'',revision:state.retake?.revision||0};
  state.pendingUpload=pending;await sendUpload(pending);
}
async function sendUpload(pending) {
  state.busy=true; $('capture').disabled=true;
  const body=new FormData();body.append('file',pending.blob,'capture');body.append('capture_id',pending.capture_id);body.append('replace',pending.replace);body.append('revision',pending.revision);
  try {
    const s=await api(`/sessions/${pending.sid}/pages`,{method:'POST',body});
    state.pendingUpload=null;state.retake=null;$('upload').multiple=true;notice('');acceptSession(s);
    state.lastCapture=Date.now();state.stableSince=0;
    if(s.settings.mode==='single')stopCamera();
  } catch(e) {notice(e.message+' The pending photo is kept in this tab for retry.',true);throw e;}
  finally {state.busy=false;$('capture').disabled=false;renderSession();}
}
async function retryUpload() {if(state.pendingUpload)try{await sendUpload(state.pendingUpload);}catch{}}
async function openCamera() {
  if(!window.isSecureContext||!navigator.mediaDevices?.getUserMedia) {
    notice('Live camera needs a secure connection trusted by this phone. Follow Connection help, or use Upload photos to take or choose a photo.');return;
  }
  stopCamera();
  try {
    const selected=state.session.settings.camera;
    try {state.stream=await navigator.mediaDevices.getUserMedia({audio:false,video:{...(selected?{deviceId:{exact:selected}}:{facingMode:{ideal:'environment'}}),width:{ideal:2560},height:{ideal:1920}}});}
    catch(e) {if(selected&&e.name!=='NotAllowedError')state.stream=await navigator.mediaDevices.getUserMedia({audio:false,video:{facingMode:{ideal:'environment'},width:{ideal:2560},height:{ideal:1920}}});else throw e;}
    $('video').srcObject=state.stream;await $('video').play();$('camera').hidden=false;
    const devices=(await navigator.mediaDevices.enumerateDevices()).filter(d=>d.kind==='videoinput');
    $('camera-select').replaceChildren(...devices.map((d,i)=>{const o=document.createElement('option');o.value=d.deviceId;o.textContent=d.label||`Camera ${i+1}`;return o;}));
    const track=state.stream.getVideoTracks()[0];$('camera-select').value=track.getSettings().deviceId;
    $('camera-note').textContent=`${track.getSettings().width} × ${track.getSettings().height} video. High-resolution still capture is used when supported. Only cameras exposed by this browser are listed.`;
    positionGuide();state.previousFrame=null;state.label='';state.labelCount=0;state.stableSince=0;
    state.liveTimer=setInterval(()=>liveCheck(),900);
  } catch(e) {
    const message=e.name==='NotAllowedError'?'Camera permission was denied. In this browser’s site settings, allow Camera, then open it again. No microphone access is needed.':e.name==='NotFoundError'?'No camera is exposed by this device. Upload a photo instead.':'The camera could not start. Close other camera apps, check permissions, and retry or upload a photo.';
    notice(message);
  }
}
function stopCamera() {
  clearInterval(state.liveTimer);state.stream?.getTracks().forEach(t=>t.stop());state.stream=null;
  if($('video'))$('video').srcObject=null;if($('camera'))$('camera').hidden=true;state.previousFrame=null;
}
function paperDimensions(s) {
  const preset={A4:[210,297],A5:[148,210],A3:[297,420],Letter:[215.9,279.4],Legal:[215.9,355.6],B5:[176,250]};
  const d=(preset[s.paper]||[s.width,s.height]).slice().sort((a,b)=>a-b);return s.orientation==='landscape'?d.reverse():d;
}
function positionGuide() {
  const [w,h]=paperDimensions(state.session.settings),v=$('video');
  // Match overlay coordinates to the contained video, excluding letterboxing.
  const area=v.parentElement, aw=area.clientWidth,ah=area.clientHeight;
  const scale=Math.min(aw/v.videoWidth,ah/v.videoHeight),vw=v.videoWidth*scale,vh=v.videoHeight*scale;
  const svg=$('detected');svg.style.width=`${vw}px`;svg.style.height=`${vh}px`;svg.style.left=`${(aw-vw)/2}px`;svg.style.top=`${(ah-vh)/2}px`;
  const gw=Math.min(vw*.78,vh*.85*w/h);$('paper-guide').style.width=`${gw}px`;$('paper-guide').style.height=`${gw*h/w}px`;
}
async function frameBlob(width=600) {
  const v=$('video'),c=document.createElement('canvas');c.width=Math.min(width,v.videoWidth);c.height=Math.round(v.videoHeight*c.width/v.videoWidth);
  c.getContext('2d').drawImage(v,0,0,c.width,c.height);return {canvas:c,blob:await new Promise(r=>c.toBlob(r,'image/jpeg',.8))};
}
async function capture() {
  if(!state.stream||state.busy||state.pendingUpload)return;
  state.lastCapture=Date.now();state.stableSince=0;state.autoArmed=false;
  const fingerprint=await frameBlob(80);
  state.lastCapturedFrame=fingerprint.canvas.getContext('2d').getImageData(0,0,fingerprint.canvas.width,fingerprint.canvas.height).data;
  const track=state.stream.getVideoTracks()[0];let blob;
  if(window.ImageCapture) {
    try {const ic=new ImageCapture(track);const caps=await ic.getPhotoCapabilities();blob=await ic.takePhoto({imageWidth:caps.imageWidth.max,imageHeight:caps.imageHeight.max});}
    catch {try{blob=await new ImageCapture(track).takePhoto();}catch{}}
  }
  if(!blob) {const {blob:frame}=await frameBlob(10000);blob=frame;notice('This browser supplied a video-resolution capture. For finer text, Upload photos may use a higher-resolution phone photo.');}
  await uploadBlob(blob);
}
async function liveCheck() {
  if(!state.stream||state.qualityBusy||state.busy||!$('video').videoWidth)return;
  const config=state.session.settings;
  if(!config.live_feedback&&!config.auto_capture){$('feedback').textContent='Manual capture';return;}
  state.qualityBusy=true;
  try {
    const {canvas,blob}=await frameBlob(480),ctx=canvas.getContext('2d');
    const pixels=ctx.getImageData(0,0,canvas.width,canvas.height).data;let motion=0;
    if(state.previousFrame&&state.previousFrame.length===pixels.length) {let n=0;for(let i=0;i<pixels.length;i+=64){motion+=Math.abs(pixels[i]-state.previousFrame[i]);n++;}motion/=n;}
    state.previousFrame=pixels;
    if(motion>12)state.autoArmed=true;
    const body=new FormData();body.append('file',blob,'frame');const q=await api('/quality',{method:'POST',body});
    $('detected-polygon').setAttribute('points',q.corners?q.corners.map(p=>p.join(',')).join(' '):'');positionGuide();
    const warning=q.warnings[0];const label=motion>9?'Hold still':warning||'Ready';
    if(label===state.label)state.labelCount++;else{state.label=label;state.labelCount=1;state.stableSince=0;}
    if(state.labelCount>=2)$('feedback').textContent=label;
    if(label==='Ready'&&q.corners&&motion<4) {
      if(!state.stableSince)state.stableSince=Date.now();
      if(config.auto_capture&&state.autoArmed&&Date.now()-state.stableSince>1800&&Date.now()-state.lastCapture>6000) {
        const fp=await frameBlob(80),current=fp.canvas.getContext('2d').getImageData(0,0,fp.canvas.width,fp.canvas.height).data;
        let changed=!state.lastCapturedFrame||current.length!==state.lastCapturedFrame.length;
        if(!changed){let delta=0,n=0;for(let i=0;i<current.length;i+=16){delta+=Math.abs(current[i]-state.lastCapturedFrame[i]);n++;}changed=delta/n>14;}
        if(changed)await capture();
      }
    } else state.stableSince=0;
  } catch { $('feedback').textContent='Quality check unavailable · use manual capture';state.stableSince=0; }
  finally {state.qualityBusy=false;}
}
async function openCrop(p) {
  if(state.session.saved||!p.original_retained) {notice('This scan is saved. Use Show original during verification when originals are retained.');return;}
  state.cropPage={...p};state.corners=(p.crop||[[0,0],[1,0],[1,1],[0,1]]).map(c=>c.slice());
  $('crop-image').src=asset(p);$('rotation').value=p.rotation;$('crop-dialog').showModal();drawCrop();
}
function drawCrop() {
  $('crop-polygon').setAttribute('points',state.corners.map(([x,y])=>`${x*1000},${y*1000}`).join(' '));
  $('crop-overlay').querySelectorAll('circle').forEach((c,i)=>{c.setAttribute('cx',state.corners[i][0]*1000);c.setAttribute('cy',state.corners[i][1]*1000);});
}
let dragging=null;
$('crop-overlay').addEventListener('pointerdown',e=>{if(e.target.dataset.corner!==undefined){dragging=Number(e.target.dataset.corner);$('crop-overlay').setPointerCapture(e.pointerId);}});
$('crop-overlay').addEventListener('pointermove',e=>{if(dragging===null)return;const r=$('crop-overlay').getBoundingClientRect();state.corners[dragging]=[Math.max(0,Math.min(1,(e.clientX-r.left)/r.width)),Math.max(0,Math.min(1,(e.clientY-r.top)/r.height))];drawCrop();});
$('crop-overlay').addEventListener('pointerup',()=>dragging=null);
$('crop-overlay').addEventListener('pointercancel',()=>dragging=null);
$('crop-form').onsubmit=e=>{e.preventDefault();act(async()=>{const p=state.cropPage;const wasReady=state.session.pages.some(p=>p.status==='ready');acceptSession(await api(`/sessions/${state.session.id}/pages/${p.id}`,{method:'PUT',body:{revision:p.revision,rotation:Number($('rotation').value),crop:state.corners}}));$('crop-dialog').close();if(wasReady)notice('Page correction applied. Process unfinished pages to refresh this page and OCR.');});};
function formEdited() {
  state.formDirty=true;state.fieldsEdited=true;
  if(state.session.approved){state.session.approved=null;renderSession();}
  $('approval-state').textContent='Changes need approval';
}
$('approval-form').addEventListener('input',formEdited);
$('approval-form').addEventListener('change',formEdited);
$('document-date').addEventListener('change',()=>{
  const s=state.session.settings;
  if(s.date_source==='document') {
    const date=$('document-date').value||(s.missing_date==='scan'?state.session.scanned_date:'');
    const name=s.naming.replaceAll('{date}',date).replaceAll('{title}',$('title').value.trim());
    $('filename').value=name.replace(/[<>:"/\\|?*\x00-\x1f]/g,'-').replace(/^[ ._-]+|[ ._-]+$/g,'').slice(0,130)||'Scan';
  }
});
$('approval-form').onsubmit=e=>{e.preventDefault();act(async()=>{
  const a={revision:state.session.revision,title:$('title').value.trim(),document_date:$('document-date').value,filename:$('filename').value.trim(),destination:$('destination').value};
  const approved=await api(`/sessions/${state.session.id}/approve`,{method:'POST',body:a});state.formDirty=false;acceptSession(approved);notice('Document approved. Save or prepare it for printing.');
});};
async function saveDocument() {
  if(state.formDirty)throw new Error('Approve your edited fields before saving.');
  state.busy=true;renderSession();
  try {const r=await api(`/sessions/${state.session.id}/save`,{method:'POST'});$('save-result').textContent=`Saved: ${r.saved}`;acceptSession(await api(`/sessions/${state.session.id}`));state.boot.stats=await api('/stats');renderSidebar();}
  finally {state.busy=false;renderSession();}
}
async function printDocument() {
  if(state.formDirty)throw new Error('Approve your edited fields before printing.');
  const result=await api(`/sessions/${state.session.id}/print`,{method:'POST'});
  if(state.boot.laptop) {
    const tab=window.open(result.url,'_blank');
    notice(tab?'Approved PDF opened. Use its Print button or Ctrl+P to choose printer, copies, pages, duplex, scaling, and paper. Printing is not confirmed.':'Print preview was blocked. Allow pop-ups, then choose Open print preview.');
  } else notice('Ready to print on laptop. Open I-SKAN there and choose Open print preview.');
  acceptSession(await api(`/sessions/${state.session.id}`));
}
const settingGroups=[
 ['Paper',[['paper','Paper size','select',['A4','A5','Letter','Legal','A3','B5','Custom']],['width','Custom width','number',.1,2000,.1],['height','Custom height','number',.1,2000,.1],['units','Custom units','select',[['mm','Millimeters'],['in','Inches']]],['orientation','Orientation','select',['portrait','landscape']]]],
 ['Capture',[['auto_capture','Automatic capture after stable quality','checkbox'],['live_feedback','Live quality feedback','checkbox']]],
 ['Appearance',[['appearance','Appearance','select',[['color','Color'],['grayscale','Grayscale'],['bw','Black & white']]],['strength','Cleanup strength (0–0.65)','number',0,.65,.05]]],
 ['Layout',[['layout','Page layout','select',[['fit','Fit without distortion'],['preserve','Preserve crop proportions']]],['margin','Margins (mm)','number',0,40,.5]]],
 ['Watermark',[['watermark','Text (blank disables)','text'],['watermark_position','Position','select',['center','top','bottom']],['watermark_size','Text size (points)','number',8,72,1],['watermark_opacity','Opacity','number',0,.8,.01]]],
 ['Page numbers',[['numbers','Show page numbers','checkbox'],['number_position','Position','select',['left','center','right']],['number_start','Starting number','number',1,9999,1]]],
 ['Export & naming',[['export','Export format','select',[['searchable','Searchable PDF'],['pdf','Image-only PDF'],['jpg','JPG page set'],['png','PNG page set']]],['quality','Image quality (65–100)','number',65,100,1],['naming','Naming pattern: {date} and {title}','text'],['date_source','Filename date source','select',[['scan','Scanned date'],['document','Document date (choose in verification)']]],['missing_date','When document date is unknown','select',[['scan','Use scanned date'],['omit','Omit date']]]]],
 ['Retention',[['retain_originals','Keep originals after saving (up to 30 days)','checkbox']]]
];
function openSettings() {
  const form=$('settings-form');form.replaceChildren();
  for(const [name,fields] of settingGroups) {
    const fs=document.createElement('fieldset'),legend=document.createElement('legend');legend.textContent=name;fs.append(legend);
    const grid=document.createElement('div');grid.className='fields';
    for(const [key,labelText,type,options,max,step] of fields) {
      const label=document.createElement('label');label.textContent=labelText;
      const input=document.createElement(type==='select'?'select':'input');input.name=key;
      if(type==='select')for(const option of options){const o=document.createElement('option');o.value=Array.isArray(option)?option[0]:option;o.textContent=Array.isArray(option)?option[1]:option;input.append(o);}
      else {input.type=type;if(type==='number'){input.min=options;input.max=max;input.step=step;}if(type==='text')input.maxLength=key==='watermark'?100:120;}
      label.append(input);grid.append(label);
    }
    fs.append(grid);form.append(fs);
  }
  const hint=document.createElement('p');hint.className='hint';hint.textContent='Appearance changes require reprocessing. Layout changes update the final preview. Black & white can remove faint marks; inspect before saving. Curved-page unwarping is not included.';form.append(hint);
  const submit=document.createElement('button');submit.className='primary';submit.textContent='Apply settings';form.append(submit);
  fillForm(form,state.session.settings);$('settings-dialog').showModal();
}
$('settings-form').onsubmit=e=>{e.preventDefault();act(async()=>{const config=readForm($('settings-form'),state.session.settings);await updateSettings(config);$('settings-dialog').close();});};
$('pair-form').onsubmit=e=>{e.preventDefault();act(async()=>{await api('/login',{method:'POST',body:{code:$('pair-code').value.trim()}});await boot();});};
$('prepare-form').onchange=prepVisibility;
$('prepare-form').onsubmit=e=>{e.preventDefault();act(async()=>{const config=readForm($('prepare-form'),state.boot.settings);const s=await api('/sessions',{method:'POST',body:config});state.boot.settings=config;state.boot.sessions.unshift(s);selectSession(s);await openCamera();});};
$('new-scan').onclick=()=>{showView('prepare');state.retake=null;fillForm($('prepare-form'),state.boot.settings);prepVisibility();};
$('camera-open').onclick=()=>act(openCamera);$('camera-close').onclick=stopCamera;
$('capture').onclick=()=>act(capture);
$('camera-select').onchange=()=>act(async()=>{state.session.settings.camera=$('camera-select').value;await updateSettings(state.session.settings);await openCamera();});
$('upload').onchange=()=>act(async()=>{const files=Array.from($('upload').files);$('upload').value='';if(state.pendingUpload){notice('Retry the pending photo before adding another.',true);return;}for(const file of files){try{await uploadBlob(file);}catch{return;}if(state.session.settings.mode==='single')break;}});
$('process').onclick=()=>act(async()=>{stopCamera();acceptSession(await api(`/sessions/${state.session.id}/process`,{method:'POST'}));});
$('update-expected').onclick=()=>act(()=>updateSettings({...state.session.settings,expected:Number($('expected-input').value)}));
$('preview-page').onchange=updatePreview;$('compare-original').onchange=updatePreview;
$('crop-cancel').onclick=()=>$('crop-dialog').close();$('full-crop').onclick=()=>{state.corners=[[0,0],[1,0],[1,1],[0,1]];drawCrop();};
$('settings-button').onclick=openSettings;$('settings-close').onclick=()=>$('settings-dialog').close();
$('page-preview-close').onclick=()=>$('page-preview-dialog').close();
$('save').onclick=()=>act(saveDocument);$('print').onclick=()=>act(printDocument);
$('save-print').onclick=()=>act(async()=>{await saveDocument();await printDocument();});
$('open-print-request').onclick=()=>act(printDocument);
$('browse-folder').onclick=()=>act(async()=>{const result=await api('/storage',{method:'POST',body:{browse:true}});state.boot.destinations=result.destinations;renderDestinations();$('destination').value=result.destination;formEdited();});
$('type-folder').onclick=()=>$('folder-dialog').showModal();$('folder-cancel').onclick=()=>$('folder-dialog').close();
$('folder-form').onsubmit=e=>{e.preventDefault();act(async()=>{const result=await api('/storage',{method:'POST',body:{path:$('folder-path').value}});state.boot.destinations=result.destinations;state.boot.settings.destination=result.destination;renderDestinations();$('destination').value=result.destination;formEdited();$('folder-dialog').close();});};
$('discard').onclick=()=>act(async()=>{if(!confirm(state.session.saved?'Remove the temporary working copy? The saved output and minimal log stay.':'Discard this working scan and its captures?'))return;const sid=state.session.id;await api(`/sessions/${sid}`,{method:'DELETE'});state.session=null;state.boot.sessions=state.boot.sessions.filter(s=>s.id!==sid);localStorage.removeItem('iskan-session');showView('prepare');renderSidebar();});
$('history-button').onclick=()=>act(async()=>{const stats=await api('/stats');state.boot.stats=stats;$('log-rows').replaceChildren(...stats.logs.map(log=>{const tr=document.createElement('tr');for(const key of ['title','page_count','scanned_date']){const td=document.createElement('td');td.textContent=log[key];tr.append(td);}return tr;}));showView('history');renderSidebar();});
window.addEventListener('resize',()=>{if(state.stream)positionGuide();});
document.addEventListener('visibilitychange',()=>{if(document.hidden)stopCamera();});
window.addEventListener('beforeunload',stopCamera);
act(boot);
