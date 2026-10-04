<script>
(() => {
  "use strict";

  // ---------- icons: one stroke family (2px, round caps) so every glyph feels related ----------
  const svg = (d, extra = "") => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" ${extra}>${d}</svg>`;
  const I = {
    radar: svg('<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><path d="M12 12 18.4 5.6"/><circle cx="12" cy="12" r="1" fill="currentColor"/>'),
    chat: svg('<path d="M21 12a8.5 8.5 0 0 1-12.6 7.4L3 21l1.6-5.1A8.5 8.5 0 1 1 21 12Z"/>'),
    person: svg('<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>'),
    back: svg('<path d="M15 5 8 12l7 7"/>'),
    chevron: svg('<path d="m9 6 6 6-6 6"/>'),
    send: svg('<path d="M5 12h13M12 5l7 7-7 7"/>', 'stroke-width="2.4"'),
    mic: svg('<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/>'),
    smile: svg('<circle cx="12" cy="12" r="9"/><path d="M8.5 14.5a4 4 0 0 0 7 0M9 9.5h.01M15 9.5h.01"/>'),
    plus: svg('<path d="M12 5v14M5 12h14"/>'),
    phone: svg('<path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2"/>'),
    video: svg('<rect x="3" y="6" width="13" height="12" rx="3"/><path d="m16 10 5-3v10l-5-3"/>'),
    speed: svg('<path d="M12 14l4-4M3.3 17a9.5 9.5 0 1 1 17.4 0"/><circle cx="12" cy="14" r="1.2" fill="currentColor"/>'),
    search: svg('<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>'),
    globe: svg('<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"/>'),
    cloudOff: svg('<path d="M3 3l18 18M8 7.2A6 6 0 0 1 17.7 11 4 4 0 0 1 20 18M17 19H7a5 5 0 0 1-1.6-9.7"/>'),
    signalOff: svg('<path d="M3 3l18 18M6 20v-3M10 20v-7M14 20v-4M18 20V8M18 4v.01"/>'),
    pin: svg('<path d="M12 21s7-6.2 7-12a7 7 0 0 0-14 0c0 5.8 7 12 7 12Z"/><circle cx="12" cy="9" r="2.5"/>'),
    mesh: svg('<circle cx="5" cy="17" r="2"/><circle cx="19" cy="17" r="2"/><circle cx="12" cy="6" r="2"/><path d="M6.2 15.4 10.8 7.7M13.2 7.7l4.6 7.7M7 17h10"/>'),
    refresh: svg('<path d="M20 11a8 8 0 1 0-2.3 5.7M20 5v6h-6"/>'),
    trash: svg('<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>'),
    palette: svg('<path d="M12 3a9 9 0 1 0 0 18c1.1 0 1.5-.8 1.5-1.6 0-1.2-1-1.6-1-2.6 0-.9.7-1.3 1.6-1.3H16a5 5 0 0 0 5-5c0-4.2-4-7.5-9-7.5Z"/><circle cx="7.5" cy="11" r="1" fill="currentColor"/><circle cx="10" cy="7" r="1" fill="currentColor"/><circle cx="15" cy="7.5" r="1" fill="currentColor"/>'),
    terminal: svg('<rect x="3" y="4" width="18" height="16" rx="3"/><path d="m7 9 3 3-3 3M13 15h4"/>'),
    copy: svg('<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V6a2 2 0 0 1 2-2h9"/>'),
    sparkle: svg('<path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6"/>'),
    book: svg('<path d="M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2V5ZM4 19a2 2 0 0 1 2-2h13M9 7h6"/>'),
    eye: svg('<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/>'),
    check: svg('<path d="m5 12 5 5 9-10"/>'),
    wallet: svg('<rect x="3" y="6" width="18" height="14" rx="3"/><path d="M3 10h18M16 15h2M7 6l9-3 1 3"/>'),
    leaf: svg('<path d="M5 19c0-8 5-14 15-14 0 10-6 15-14 15M5 19l7-7"/>'),
    plusBox: svg('<rect x="3" y="3" width="18" height="18" rx="5"/><path d="M12 8v8M8 12h8"/>'),
    drop: svg('<path d="M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11Z"/>'),
    flame: svg('<path d="M12 21a6 6 0 0 0 6-6c0-4-3-6-4-10-2 2-3 4-3 6-1-1-2-2-2-3-2 2-3 4-3 7a6 6 0 0 0 6 6Z"/>'),
    tent: svg('<path d="M3 20 12 4l9 16H3ZM12 4v16M9 20l3-6 3 6"/>'),
    compass: svg('<circle cx="12" cy="12" r="9"/><path d="m15.5 8.5-2 5-5 2 2-5 5-2Z"/>'),
    flag: svg('<path d="M5 21V4M5 4h11l-2 4 2 4H5"/>'),
    bolt: svg('<path d="M13 3 5 14h6l-1 7 8-11h-6l1-7Z"/>'),
    alert: svg('<path d="M12 4 2.5 20h19L12 4ZM12 10v4M12 17h.01"/>'),
    flash: svg('<path d="M8 3h8l-2 6h4L9 21l2-8H7l1-10Z"/>'),
    battery: svg('<rect x="2" y="7" width="17" height="10" rx="2.5"/><path d="M22 11v2M5 10v4M8 10v4"/>'),
    chart: svg('<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>'),
    game: svg('<rect x="2" y="7" width="20" height="11" rx="5"/><path d="M7 11v3M5.5 12.5h3M16 12h.01M18 14h.01"/>'),
    bot: svg('<rect x="4" y="8" width="16" height="12" rx="4"/><path d="M12 4v4M9 14h.01M15 14h.01"/>'),
  };
  // Message status, Signal style: dotted ring while sending, ring + check when sent, two rings when delivered.
  const TICK = {
    sending: '<svg viewBox="0 0 16 16" aria-label="Sending"><circle cx="8" cy="8" r="6" fill="none" stroke="currentColor" stroke-width="1.5" stroke-dasharray="2 2.4"/></svg>',
    sent: '<svg viewBox="0 0 16 16" aria-label="Sent"><circle cx="8" cy="8" r="6" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="m5.4 8.2 1.8 1.8 3.5-3.6" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    delivered: '<svg viewBox="0 0 22 16" style="width:21px" aria-label="Delivered"><circle cx="7" cy="8" r="6" fill="none" stroke="currentColor" stroke-width="1.5"/><circle cx="14" cy="8" r="6.75" fill="var(--canvas)"/><circle cx="14" cy="8" r="6" fill="currentColor"/><path d="m11.4 8.2 1.8 1.8 3.5-3.6" fill="none" stroke="var(--canvas)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    read: '<svg viewBox="0 0 22 16" style="width:21px" aria-label="Read"><circle cx="7" cy="8" r="6" fill="currentColor"/><circle cx="14" cy="8" r="6.75" fill="var(--canvas)"/><circle cx="14" cy="8" r="6" fill="currentColor"/><path d="m11.4 8.2 1.8 1.8 3.5-3.6" fill="none" stroke="var(--canvas)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    pending: '<svg viewBox="0 0 16 16" aria-label="Waiting"><circle cx="8" cy="8" r="6" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="M8 5v3l2 1.5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>',
    queued: '<svg viewBox="0 0 16 16" aria-label="Waiting"><circle cx="8" cy="8" r="6" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="M8 5v3l2 1.5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>',
  };

