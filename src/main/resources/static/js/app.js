// Interaksi di sisi klien (tanpa framework).
(function () {
  var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var canHover = window.matchMedia('(hover: hover) and (pointer: fine)').matches;

  // ------------------------------------------------------------------ dialog konfirmasi: form[data-confirm]
  var dlg = null;
  function konfirmasi(pesan, labelOk, lanjut) {
    if (typeof HTMLDialogElement !== 'function') { if (window.confirm(pesan)) lanjut(); return; }
    if (!dlg) {
      dlg = document.createElement('dialog');
      dlg.className = 'konfirmasi';
      dlg.innerHTML = '<span class="chip-icon"><svg class="i"><use href="#i-alert"/></svg></span>' +
        '<h3>Anda yakin?</h3><p></p><div class="actions">' +
        '<button type="button" class="btn light" value="batal">Batal</button>' +
        '<button type="button" class="btn danger" value="ok">Ya, lanjutkan</button></div>';
      document.body.appendChild(dlg);
      // Aksi dijalankan langsung dari klik tombol, tidak menunggu event "close"
      // (event itu bisa tertunda/tidak terkirim di sebagian browser).
      dlg.addEventListener('click', function (e) {
        if (e.target === dlg) { dlg.close(); return; }      // klik di luar kotak = batal
        var b = e.target.closest('button[value]');
        if (!b) return;
        var aksi = dlg._lanjut;
        dlg._lanjut = null;
        dlg.close();
        if (b.value === 'ok' && aksi) aksi();
      });
      dlg.addEventListener('cancel', function () { dlg._lanjut = null; }); // tombol Esc
    }
    dlg.querySelector('p').textContent = pesan;
    dlg.querySelector('[value=ok]').textContent = labelOk || 'Ya, lanjutkan';
    dlg._lanjut = lanjut;
    dlg.showModal();
    dlg.querySelector('[value=batal]').focus();
  }

  // ------------------------------------------------------------------ status memuat & cegah klik ganda
  function tandaiMemuat(form, submitter) {
    var btn = submitter || form.querySelector('button[type=submit], button:not([type])');
    form.dataset.terkirim = '1';
    if (!btn) return;
    btn.setAttribute('aria-busy', 'true');
    var teks = btn.getAttribute('data-memuat');
    if (teks) btn.textContent = teks;
  }

  document.addEventListener('submit', function (e) {
    var form = e.target;
    if (form.dataset.terkirim) { e.preventDefault(); return; }   // klik ganda
    var msg = form.getAttribute('data-confirm');
    if (msg && !form.dataset.dikonfirmasi) {
      e.preventDefault();
      var submitter = e.submitter;
      konfirmasi(msg, form.getAttribute('data-confirm-ok'), function () {
        form.dataset.dikonfirmasi = '1';
        tandaiMemuat(form, submitter);
        HTMLFormElement.prototype.submit.call(form);
      });
      return;
    }
    // Unduhan (PDF/Excel) tidak berpindah halaman: jangan kunci tombolnya.
    if (form.hasAttribute('data-unduh')) return;
    tandaiMemuat(form, e.submitter);
  });
  // Kembali ke halaman via tombol "back" browser (bfcache): pulihkan tombol.
  window.addEventListener('pageshow', function (e) {
    if (!e.persisted) return;
    document.querySelectorAll('form[data-terkirim]').forEach(function (f) {
      delete f.dataset.terkirim; delete f.dataset.dikonfirmasi;
      f.querySelectorAll('[aria-busy]').forEach(function (b) { b.removeAttribute('aria-busy'); });
    });
  });

  // ------------------------------------------------------------------ pesan: tutup & hilang otomatis
  function tutupAlert(a) {
    a.classList.add('hilang');
    setTimeout(function () { a.remove(); }, 320);
  }
  document.addEventListener('click', function (e) {
    var x = e.target.closest('[data-tutup]');
    if (x) tutupAlert(x.closest('.alert'));
  });
  document.querySelectorAll('.alert[data-hilang]').forEach(function (a) {
    setTimeout(function () { if (document.body.contains(a)) tutupAlert(a); }, 6000);
  });

  // ------------------------------------------------------------------ tampilkan / sembunyikan password
  document.addEventListener('click', function (e) {
    var t = e.target.closest('[data-lihat-pw]');
    if (!t) return;
    e.preventDefault();
    var input = t.parentElement.querySelector('input');
    var lihat = input.type === 'password';
    input.type = lihat ? 'text' : 'password';
    t.setAttribute('aria-label', lihat ? 'Sembunyikan password' : 'Tampilkan password');
    t.querySelector('use').setAttribute('href', lihat ? '#i-eye-off' : '#i-eye');
    input.focus();
  });

  // ------------------------------------------------------------------ kembali & cetak
  document.addEventListener('click', function (e) {
    var k = e.target.closest('[data-kembali]');
    if (k && document.referrer && document.referrer.indexOf(location.origin) === 0 && history.length > 1) {
      e.preventDefault();
      history.back();
    }
    var c = e.target.closest('[data-cetak]');
    if (c) { e.preventDefault(); window.print(); }
  });
  // Saat dicetak, rincian perhitungan selalu ikut.
  var dibuka = [];
  window.addEventListener('beforeprint', function () {
    dibuka = [];
    document.querySelectorAll('details.teknis:not([open])').forEach(function (d) { d.open = true; dibuka.push(d); });
  });
  window.addEventListener('afterprint', function () { dibuka.forEach(function (d) { d.open = false; }); });

  // ------------------------------------------------------------------ sesi petugas: berakhir saat tidak aktif
  // Server mengakhiri sesi petugas setelah 30 menit tanpa permintaan. Tab yang dibiarkan terbuka
  // diarahkan ke halaman masuk petugas pada saat yang sama, agar tidak berakhir di halaman 404.
  var bar = document.querySelector('[data-sesi-menit]');
  if (bar) {
    var batasMs = parseInt(bar.getAttribute('data-sesi-menit'), 10) * 60 * 1000;
    var mulai = Date.now();
    // Hanya permintaan ke server yang memperpanjang sesi; muat ulang halaman = aktivitas baru.
    setInterval(function () {
      if (Date.now() - mulai >= batasMs) location.href = '/petugas/masuk?waktuHabis';
    }, 30 * 1000);
  }

  // ------------------------------------------------------------------ data uji pemeriksaan klinis (admin/pakar)
  var formKlinis = document.getElementById('form-klinis');
  if (formKlinis) {
    var contoh = {
      risiko: { Age: 58, Sex: 'M', ChestPainType: 'ASY', RestingBP: 150, Cholesterol: 270, FastingBS: '1',
        RestingECG: 'ST', MaxHR: 118, ExerciseAngina: 'Y', Oldpeak: 2.5, ST_Slope: 'Flat' },
      normal: { Age: 41, Sex: 'F', ChestPainType: 'ATA', RestingBP: 118, Cholesterol: 190, FastingBS: '0',
        RestingECG: 'Normal', MaxHR: 172, ExerciseAngina: 'N', Oldpeak: 0, ST_Slope: 'Up' }
    };
    document.querySelectorAll('[data-contoh]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var d = contoh[btn.getAttribute('data-contoh')];
        Object.keys(d).forEach(function (k) {
          formKlinis.querySelectorAll('[name="' + k + '"]').forEach(function (el) {
            if (el.type === 'radio') el.checked = (el.value === String(d[k]));
            else el.value = d[k];
          });
        });
      });
    });
  }

  // ------------------------------------------------------------------ pencarian tabel
  document.querySelectorAll('[data-filter]').forEach(function (input) {
    var rows = document.querySelectorAll(input.getAttribute('data-filter') + ' tbody tr');
    input.addEventListener('input', function () {
      var q = input.value.toLowerCase();
      rows.forEach(function (tr) {
        tr.style.display = tr.textContent.toLowerCase().indexOf(q) === -1 ? 'none' : '';
      });
    });
  });

  // ------------------------------------------------------------------ menu "Lainnya"
  document.addEventListener('click', function (e) {
    document.querySelectorAll('details.more[open]').forEach(function (d) {
      if (!d.contains(e.target)) d.removeAttribute('open');
    });
  });

  // ------------------------------------------------------------------ tooltip grafik: [data-tip]
  var tip = null;
  function tampilTip(el) {
    if (!tip) { tip = document.createElement('div'); tip.className = 'tip'; tip.setAttribute('role', 'tooltip'); document.body.appendChild(tip); }
    tip.textContent = el.getAttribute('data-tip');
    tip.style.display = 'block';
    var r = el.getBoundingClientRect(), t = tip.getBoundingClientRect();
    var x = Math.min(Math.max(8, r.left + r.width / 2 - t.width / 2), window.innerWidth - t.width - 8);
    var y = r.top - t.height - 8;
    if (y < 8) y = r.bottom + 8;
    tip.style.left = x + 'px';
    tip.style.top = y + 'px';
  }
  function sembunyiTip() { if (tip) tip.style.display = 'none'; }
  document.querySelectorAll('[data-tip]').forEach(function (el) {
    el.addEventListener('mouseenter', function () { tampilTip(el); });
    el.addEventListener('mouseleave', sembunyiTip);
    el.addEventListener('focus', function () { tampilTip(el); });
    el.addEventListener('blur', sembunyiTip);
    el.addEventListener('click', function () { tampilTip(el); });
  });
  window.addEventListener('scroll', sembunyiTip, { passive: true });

  // ------------------------------------------------------------------ kartu muncul bertahap
  document.querySelectorAll('main .card, main .q, .hero-copy').forEach(function (el, i) {
    el.style.setProperty('--i', Math.min(i, 14));
  });

  // ------------------------------------------------------------------ helix tinta (beranda)
  // Heliks ganda hijau seperti tinta di air: dua untai berputar perlahan, terus melepaskan kepulan
  // butir yang hanyut mengikuti medan alir berubah-ubah, dan menyibak di sekitar penunjuk.
  // Gerak berbasis waktu (dt), jalan hanya saat terlihat, berhenti bila gerak dikurangi.
  var WARNA_TINTA = [[23, 77, 66], [31, 107, 92], [52, 128, 84], [98, 156, 72], [150, 190, 86], [196, 216, 120]];
  function spriteTinta(c) {
    var k = document.createElement('canvas'); k.width = k.height = 32;
    var g = k.getContext('2d'), gr = g.createRadialGradient(16, 16, 0, 16, 16, 16);
    gr.addColorStop(0, 'rgba(' + c + ',1)'); gr.addColorStop(.35, 'rgba(' + c + ',.55)'); gr.addColorStop(1, 'rgba(' + c + ',0)');
    g.fillStyle = gr; g.fillRect(0, 0, 32, 32);
    return k;
  }
  var SPRITE_TINTA = WARNA_TINTA.map(spriteTinta);

  function helix(cv) {
    var ragam = cv.getAttribute('data-helix');           // 'hero' | 'kecil'
    var root = cv.parentElement, ctx = cv.getContext('2d');
    var W = 0, H = 0, dpr = 1, jalan = false, terlihat = true, waktuLalu = 0, t0 = 0;
    var untai = [], kepul = [], MAKS = 0, px = -9999, py = -9999, ada = 0, adaT = 0;
    var ax, ay, bx, by, R, PUTARAN;

    function ukur() {
      var r = root.getBoundingClientRect();
      if (!r.width) return;
      dpr = Math.min(window.devicePixelRatio || 1, 2);
      W = r.width; H = r.height;
      cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      var kecil = W < 760;
      if (ragam === 'hero') {
        // sumbu diagonal: kanan atas -> tengah bawah, melewati belakang foto jantung
        ax = W * (kecil ? 1.05 : .98); ay = -H * .08; bx = W * (kecil ? .35 : .56); by = H * 1.08;
        R = Math.min(W, H) * (kecil ? .16 : .15);
      } else {
        // di belakang kartu formulir (kanan) agar judul di kiri tetap bersih
        ax = W * (kecil ? .9 : .98); ay = -H * .1; bx = W * (kecil ? .55 : .62); by = H * 1.1; R = Math.min(W, H) * .16;
      }
      PUTARAN = 2.6;
      var n = Math.round(Math.min(1500, Math.max(500, (W * H) / (kecil ? 900 : 700))));
      untai = [];
      for (var j = 0; j < n; j++) {
        untai.push({ u: Math.random(), s: j % 2, j1: (Math.random() - .5) * 2, j2: (Math.random() - .5) * 2,
          tangga: Math.random() < .12 ? Math.random() : -1, w: 1 + (Math.random() * 3 | 0), r: .6 + Math.random() * 1.4 });
      }
      MAKS = kecil ? 650 : 1300;
    }

    function titikUntai(u, sisi, t, out) {
      var cx = ax + (bx - ax) * u, cy = ay + (by - ay) * u;
      var dx = bx - ax, dy = by - ay, L = Math.sqrt(dx * dx + dy * dy), nx = -dy / L, ny = dx / L;
      var fase = u * PUTARAN * Math.PI * 2 + t * .45 + sisi * Math.PI;
      var c = Math.cos(fase), z = Math.sin(fase);
      out.x = cx + nx * R * c; out.y = cy + ny * R * c; out.z = z;
      return out;
    }
    // medan alir halus (gabungan sinus, murah) untuk kepulan tinta
    function alir(x, y, t) {
      var a = Math.sin(x * .0042 + t * .21) + Math.sin(y * .0051 - t * .17) + Math.sin((x + y) * .0023 + t * .13);
      return a * 1.6;
    }
    var tmp = { x: 0, y: 0, z: 0 };

    function gambar(now) {
      var dt = Math.min(.05, (now - (waktuLalu || now)) / 1000); waktuLalu = now;
      if (!t0) t0 = now;
      var t = (now - t0) / 1000;
      ada += (adaT - ada) * (1 - Math.exp(-5 * dt));
      ctx.clearRect(0, 0, W, H);

      // lepaskan kepulan baru dari untai
      var lahir = Math.min(MAKS - kepul.length, Math.round((ragam === 'hero' ? 90 : 45) * dt * 10));
      for (var b = 0; b < lahir; b++) {
        var p = untai[(Math.random() * untai.length) | 0];
        titikUntai(p.u, p.s, t, tmp);
        kepul.push({ x: tmp.x, y: tmp.y, vx: (Math.random() - .5) * 14, vy: (Math.random() - .5) * 14, umur: 0,
          hidup: 2.5 + Math.random() * 3.5, w: 2 + (Math.random() * 4 | 0), r: .8 + Math.random() * 2.4 });
      }
      var rad = 120, rad2 = rad * rad;
      // kepulan: hanyut di medan alir, menyibak di sekitar penunjuk, memudar
      for (var i = kepul.length - 1; i >= 0; i--) {
        var k = kepul[i];
        k.umur += dt;
        if (k.umur > k.hidup) { kepul[i] = kepul[kepul.length - 1]; kepul.pop(); continue; }
        var sudut = alir(k.x, k.y, t);
        k.vx += Math.cos(sudut) * 22 * dt; k.vy += Math.sin(sudut) * 22 * dt;
        if (ada > .02) {
          var dx = k.x - px, dy = k.y - py, d2 = dx * dx + dy * dy;
          if (d2 < rad2) { var dd = Math.sqrt(d2) || 1, f = (1 - dd / rad) * 420 * ada * dt; k.vx += dx / dd * f; k.vy += dy / dd * f; }
        }
        k.vx *= 1 - 1.4 * dt; k.vy *= 1 - 1.4 * dt;
        k.x += k.vx * dt; k.y += k.vy * dt;
        var fase = k.umur / k.hidup, alfa = fase < .15 ? fase / .15 : 1 - (fase - .15) / .85;
        var s = k.r * (1 + fase * 2.2) * 2.4;
        ctx.globalAlpha = alfa * .32;
        ctx.drawImage(SPRITE_TINTA[k.w], k.x - s, k.y - s, s * 2, s * 2);
      }
      // untai heliks: butir rapat, depan lebih besar & pekat
      for (var j = 0; j < untai.length; j++) {
        var q = untai[j];
        if (q.tangga >= 0) {                     // anak tangga di antara dua untai
          titikUntai(q.u, 0, t, tmp); var x1 = tmp.x, y1 = tmp.y, z1 = tmp.z;
          titikUntai(q.u, 1, t, tmp);
          var m = q.tangga;
          tmp.x = x1 + (tmp.x - x1) * m; tmp.y = y1 + (tmp.y - y1) * m; tmp.z = z1 + (tmp.z - z1) * m;
        } else {
          titikUntai(q.u, q.s, t, tmp);
        }
        var X = tmp.x + q.j1 * 4, Y = tmp.y + q.j2 * 4, dep = (tmp.z + 1) / 2;
        if (ada > .02) {
          var ex = X - px, ey = Y - py, e2 = ex * ex + ey * ey;
          if (e2 < rad2) { var ed = Math.sqrt(e2) || 1, g = (1 - ed / rad); g = g * g * 30 * ada; X += ex / ed * g; Y += ey / ed * g; }
        }
        var ukuran = q.r * (.7 + dep * .9) * 2.2;
        ctx.globalAlpha = .25 + dep * .55;
        ctx.drawImage(SPRITE_TINTA[dep > .6 ? q.w - 1 : q.w], X - ukuran, Y - ukuran, ukuran * 2, ukuran * 2);
      }
      ctx.globalAlpha = 1;
    }
    function frame(now) { if (!jalan) return; gambar(now); requestAnimationFrame(frame); }
    function mulai() { if (jalan || !terlihat || document.hidden || reduce) return; jalan = true; waktuLalu = 0; requestAnimationFrame(frame); }
    function berhenti() { jalan = false; }
    cv._ujiBiaya = function (n) { var a = performance.now(); for (var i = 0; i < n; i++) gambar(a + i * 16.7); return (performance.now() - a) / n; };

    root.addEventListener('pointermove', function (e) {
      var r = root.getBoundingClientRect(); px = e.clientX - r.left; py = e.clientY - r.top; adaT = 1;
    });
    root.addEventListener('pointerleave', function () { adaT = 0; });
    ukur();
    if (reduce) { gambar(performance.now()); return; }   // gerak dikurangi: satu gambar diam
    if ('ResizeObserver' in window) {
      var tunda; new ResizeObserver(function () { clearTimeout(tunda); tunda = setTimeout(ukur, 150); }).observe(root);
    }
    if ('IntersectionObserver' in window) {
      new IntersectionObserver(function (en) { terlihat = en[0].isIntersecting; if (terlihat) mulai(); else berhenti(); }).observe(root);
    }
    document.addEventListener('visibilitychange', function () { if (document.hidden) berhenti(); else mulai(); });
    mulai();
  }
  document.querySelectorAll('[data-helix]').forEach(helix);

  // ------------------------------------------------------------------ efek scroll beranda
  var lapis = [].slice.call(document.querySelectorAll('[data-lapis]'));
  var ungkap = [].slice.call(document.querySelectorAll('[data-ungkap]'));
  var rel = document.querySelector('[data-rel]');
  var paralaks = [].slice.call(document.querySelectorAll('[data-paralaks]'));
  var layarHP = window.matchMedia('(max-width: 860px)');

  // pecah teks menjadi kata agar bisa menyala satu per satu
  ungkap.forEach(function (el) {
    var kata = el.textContent.trim().split(/\s+/);
    el.textContent = '';
    kata.forEach(function (k, i) {
      var s = document.createElement('span'); s.className = 'kata'; s.textContent = k;
      el.appendChild(s); if (i < kata.length - 1) el.appendChild(document.createTextNode(' '));
    });
    el._kata = [].slice.call(el.querySelectorAll('.kata'));
  });

  // rel layanan: tinggi bagian = panjang geser + satu layar, isi lengket lalu digeser horizontal
  function ukurRel() {
    if (!rel) return;
    var isi = rel.querySelector('.rel');
    if (layarHP.matches || reduce) { rel.style.height = ''; isi.style.transform = ''; return; }
    var geser = Math.max(0, isi.scrollWidth - window.innerWidth);
    rel._geser = geser;
    rel.style.height = (window.innerHeight + geser) + 'px';
  }

  var antre = false;
  function gulir() {
    antre = false;
    var vh = window.innerHeight;
    // lapisan berikut naik menutupi: lapisan sebelumnya sedikit mengecil & meredup
    for (var i = 0; i < lapis.length - 1; i++) {
      var atasBerikut = lapis[i + 1].getBoundingClientRect().top;
      var p = Math.min(1, Math.max(0, 1 - atasBerikut / vh));
      lapis[i].style.transform = p > 0 ? 'scale(' + (1 - p * .04).toFixed(4) + ')' : '';
      lapis[i].style.setProperty('--tutup', (p * .18).toFixed(3));
    }
    // kata menyala sesuai posisi paragraf di layar
    ungkap.forEach(function (el) {
      var r = el.getBoundingClientRect();
      var p = Math.min(1, Math.max(0, (vh * .85 - r.top) / (r.height + vh * .35)));
      var n = Math.round(p * el._kata.length);
      for (var j = 0; j < el._kata.length; j++) el._kata[j].classList.toggle('nyala', j < n);
    });
    // rel horizontal
    if (rel && rel._geser && !layarHP.matches) {
      var rr = rel.getBoundingClientRect();
      var q = Math.min(1, Math.max(0, -rr.top / (rr.height - vh)));
      rel.querySelector('.rel').style.transform = 'translate3d(' + (-q * rel._geser).toFixed(1) + 'px,0,0)';
    }
    // paralaks foto lebar
    paralaks.forEach(function (img) {
      var r = img.parentElement.getBoundingClientRect();
      var q = (r.top + r.height / 2 - vh / 2) / vh;
      img.style.transform = 'translate3d(0,' + (q * -8 - 7).toFixed(2) + '%,0)';
    });
  }
  function mintaGulir() { if (!antre) { antre = true; requestAnimationFrame(gulir); } }
  if (lapis.length || ungkap.length || rel) {
    if (reduce) {
      ungkap.forEach(function (el) { el._kata.forEach(function (k) { k.classList.add('nyala'); }); });
    } else {
      ukurRel();
      window.addEventListener('scroll', mintaGulir, { passive: true });
      window.addEventListener('resize', function () { ukurRel(); mintaGulir(); });
      window.addEventListener('load', function () { ukurRel(); mintaGulir(); });
      gulir();
    }
  }

  // judul hero naik kata per kata (teks asli tetap utuh untuk pembaca layar lewat aria-label)
  document.querySelectorAll('[data-judul-muncul]').forEach(function (h) {
    if (reduce) return;
    var teks = h.textContent.trim();
    h.setAttribute('aria-label', teks);
    h.textContent = '';
    teks.split(/\s+/).forEach(function (k, i, semua) {
      var m = document.createElement('span'); m.className = 'kata-masker'; m.setAttribute('aria-hidden', 'true');
      var s = document.createElement('span'); s.textContent = k; s.style.setProperty('--i', i);
      m.appendChild(s); h.appendChild(m);
      if (i < semua.length - 1) h.appendChild(document.createTextNode(' '));
    });
    requestAnimationFrame(function () { requestAnimationFrame(function () { h.classList.add('tampil'); }); });
  });

  // garis progres gulir (beranda)
  var progres = document.querySelector('[data-progres]');
  if (progres && !reduce) {
    var antreP = false;
    var perbarui = function () {
      antreP = false;
      var maks = document.documentElement.scrollHeight - window.innerHeight;
      progres.style.transform = 'scaleX(' + (maks > 0 ? Math.min(1, window.scrollY / maks) : 0).toFixed(4) + ')';
    };
    window.addEventListener('scroll', function () { if (!antreP) { antreP = true; requestAnimationFrame(perbarui); } }, { passive: true });
    perbarui();
  }

  // tombol magnet: condong ke arah penunjuk, kembali saat penunjuk pergi (hanya mouse)
  if (canHover && !reduce) {
    document.querySelectorAll('[data-magnet]').forEach(function (b) {
      b.addEventListener('pointermove', function (e) {
        var r = b.getBoundingClientRect();
        var dx = (e.clientX - r.left - r.width / 2) / r.width, dy = (e.clientY - r.top - r.height / 2) / r.height;
        b.style.transform = 'translate(' + (dx * 10).toFixed(1) + 'px,' + (dy * 8).toFixed(1) + 'px)';
      });
      b.addEventListener('pointerleave', function () { b.style.transform = ''; });
    });
  }

  // baris tabel yang bisa diklik (dashboard admin)
  document.querySelectorAll('tr[data-href]').forEach(function (tr) {
    tr.addEventListener('click', function (e) { if (!e.target.closest('a, button')) location.href = tr.getAttribute('data-href'); });
  });
  // elemen muncul saat masuk layar
  var muncul = document.querySelectorAll('[data-muncul]');
  if (muncul.length) {
    if (reduce || !('IntersectionObserver' in window)) muncul.forEach(function (el) { el.classList.add('tampak'); });
    else {
      var io2 = new IntersectionObserver(function (en) {
        en.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add('tampak'); io2.unobserve(e.target); } });
      }, { threshold: .15, rootMargin: '0px 0px -8% 0px' });
      muncul.forEach(function (el) { io2.observe(el); });
    }
  }

  // jejak foto yang mengikuti penunjuk (hanya mouse/trackpad)
  var bagJejak = document.querySelector('[data-jejak]');
  if (bagJejak && canHover && !reduce) {
    var sumberJejak = [].slice.call(bagJejak.querySelectorAll('img')).map(function (i) { return i.currentSrc || i.src; });
    var lx = 0, ly = 0, urut = 0, terakhir = 0;
    bagJejak.addEventListener('pointermove', function (e) {
      if (e.target.closest('.kartu-foto, a, button')) return;          // jangan menutupi kartu yang sedang dilihat
      var dx = e.clientX - lx, dy = e.clientY - ly, now = performance.now();
      if (dx * dx + dy * dy < 110 * 110 || now - terakhir < 90) return;
      lx = e.clientX; ly = e.clientY; terakhir = now;
      var f = document.createElement('div'); f.className = 'jejak-foto';
      f.style.left = e.clientX + 'px'; f.style.top = e.clientY + 'px';
      f.style.setProperty('--r', ((Math.random() - .5) * 10).toFixed(1) + 'deg');
      var im = new Image(); im.src = sumberJejak[urut++ % sumberJejak.length]; im.alt = '';
      f.appendChild(im); document.body.appendChild(f);
      f.addEventListener('animationend', function () { f.remove(); });
    });
  }

  // ------------------------------------------------------------------ jantung: ketuk untuk memacu detak
  document.querySelectorAll('[data-heart]').forEach(function (heart) {
    // Ketuk/klik: detak dipacu sebentar lalu kembali normal.
    var timer;
    heart.addEventListener('click', function () {
      if (reduce) return;
      var normal = heart.dataset.beat || '';
      heart.style.setProperty('--beat', '.45s');
      clearTimeout(timer);
      timer = setTimeout(function () {
        if (normal) heart.style.setProperty('--beat', normal);
        else heart.style.removeProperty('--beat');
      }, 2600);
    });
  });

  // ------------------------------------------------------------------ angka dihitung naik
  function easeOut(t) { return 1 - Math.pow(1 - t, 3); }
  function countUp(el, decimal) {
    var raw = el.textContent.trim();
    var target = parseFloat(raw.replace(/\./g, '').replace(',', '.'));
    if (!isFinite(target) || target === 0 || reduce) return;
    var start = null, dur = 1100;
    function frame(ts) {
      if (start === null) start = ts;
      var p = Math.min((ts - start) / dur, 1);
      var v = target * easeOut(p);
      el.textContent = decimal
        ? v.toFixed(2).replace('.', ',')
        : Math.round(v).toLocaleString('id-ID');
      if (p < 1) requestAnimationFrame(frame);
      else el.textContent = raw;
    }
    requestAnimationFrame(frame);
  }
  var io = 'IntersectionObserver' in window ? new IntersectionObserver(function (entries) {
    entries.forEach(function (en) {
      if (!en.isIntersecting) return;
      io.unobserve(en.target);
      start(en.target);
    });
  }, { threshold: 0.3 }) : null;

  function start(el) {
    if (el.hasAttribute('data-count')) countUp(el, false);
    else if (el.hasAttribute('data-count-dec')) countUp(el, true);
    else if (el.matches('.gauge .val')) {
      var target = el.style.strokeDasharray;
      el.style.transition = 'none';
      el.style.strokeDasharray = '0 100';
      el.getBoundingClientRect();
      el.style.transition = '';
      requestAnimationFrame(function () { el.style.strokeDasharray = target; });
    } else if (el.matches('.cc-stack')) {
      var hgt = el.style.height;
      el.style.transition = 'none';
      el.style.height = '0';
      el.getBoundingClientRect();
      el.style.transition = '';
      requestAnimationFrame(function () { el.style.height = hgt; });
    } else if (el.matches('.bar > i, .gbar .track i')) {
      var w = el.style.width;
      el.style.transition = 'none';
      el.style.width = '0';
      el.getBoundingClientRect();
      el.style.transition = '';
      requestAnimationFrame(function () { el.style.width = w; });
    }
  }
  if (!reduce) {
    document.querySelectorAll('[data-count], [data-count-dec], .gauge .val, .bar > i, .cc-stack, .gbar .track i').forEach(function (el) {
      if (io) io.observe(el); else start(el);
    });
  }

  // ------------------------------------------------------------------ form diagnosa
  var form = document.getElementById('form-diagnosa');
  if (form) {
    var counter = document.getElementById('jumlah-dipilih');
    var heartBox = document.getElementById('jantung-diagnosa');
    var heart = heartBox ? heartBox.querySelector('[data-heart]') : null;
    var icon = document.getElementById('detak-ikon');

    var update = function () {
      var n = 0, risiko = 0, normal = 0;
      form.querySelectorAll('.q-group').forEach(function (grp) {
        var kat = grp.getAttribute('data-kategori');
        grp.querySelectorAll('.q').forEach(function (q) {
          var c = q.querySelector('input:checked');
          var v = c ? parseFloat(c.value) : 0;
          q.classList.toggle('answered', v !== 0);
          q.classList.toggle('negatif', v < 0);
          if (v !== 0) n++;
          if (kat === 'RISIKO') risiko += v;
          else if (kat === 'NORMAL') normal += v;
        });
      });
      if (counter) counter.textContent = n;

      // Detak makin cepat bila indikator risiko dominan, makin tenang bila indikator normal.
      var beat = Math.max(0.5, Math.min(1.35, 1 - risiko * 0.09 + normal * 0.06));
      var b = beat.toFixed(2) + 's';
      if (heart) {
        heart.dataset.beat = b;
        heart.style.setProperty('--beat', b);
        heart.classList.toggle('risk', risiko > normal);
        heart.classList.toggle('ok', normal > risiko);
      }
      if (icon) icon.style.setProperty('--beat', b);
    };
    form.addEventListener('change', update);
    form.addEventListener('reset', function () { setTimeout(update, 0); });
    update();

    // Ringkasan data fisik langsung (umur, IMT, kategori tekanan darah) — rumus sama dengan server.
    var outUmur = document.getElementById('out-umur'), outImt = document.getElementById('out-imt'),
        outImtField = document.getElementById('out-imt-field');
    var angka = function (id) { var el = document.getElementById(id); var v = el ? parseFloat(String(el.value).replace(',', '.')) : NaN; return isNaN(v) ? null : v; };
    var isi = function (el, utama, ket, kelas) {
      if (!el) return;
      el.textContent = utama;
      if (ket) { var s = document.createElement('small'); s.textContent = ket; if (kelas) s.className = kelas; el.appendChild(s); }
    };
    var fisik = function () {
      var lahir = document.getElementById('in-lahir');
      var umur = null;
      if (lahir && lahir.value) {
        var l = new Date(lahir.value + 'T00:00:00'), k = new Date();
        umur = k.getFullYear() - l.getFullYear();
        if (k.getMonth() < l.getMonth() || (k.getMonth() === l.getMonth() && k.getDate() < l.getDate())) umur--;
        if (umur < 0 || umur > 130) umur = null;
      }
      isi(outUmur, umur === null ? '—' : umur + ' tahun');

      var t = angka('in-tinggi'), b = angka('in-berat');
      if (t && b && t >= 50 && b >= 2) {
        var imt = Math.round(b / Math.pow(t / 100, 2) * 10) / 10;
        var kat = imt < 17 ? 'Kurus berat' : imt < 18.5 ? 'Kurus ringan' : imt <= 25 ? 'Normal' : imt <= 27 ? 'Gemuk' : 'Obesitas';
        isi(outImt, imt.toFixed(1).replace('.', ','), kat, kat === 'Normal' ? 'baik' : 'waspada');
        if (outImtField) outImtField.textContent = imt.toFixed(1).replace('.', ',') + ' kg/m² · ' + kat;
      } else {
        isi(outImt, '—');
        if (outImtField) outImtField.textContent = '—';
      }
    };
    form.addEventListener('input', fisik);
    form.addEventListener('change', fisik);
    fisik();
  }
})();
