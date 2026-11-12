# AutoTainment

Dashboard Android Auto **percuma & open-source** — dibina untuk Honda Civic FE, tapi berfungsi pada mana-mana kereta yang support Android Auto.

## Ciri v0.1

| Ciri | Keterangan |
|---|---|
| Jam & tarikh | Jam besar dengan saat, tarikh Melayu + tarikh Hijri |
| Waktu solat JAKIM | Zon auto ikut GPS (atau set manual, cth `PLS01`), countdown ke waktu seterusnya |
| Azan | Suara "Telah masuk waktu solat …" atau fail audio azan pilihan sendiri |
| Cuaca | Suhu, keadaan cuaca (BM), kelembapan — Open-Meteo |
| Now playing | Lagu, artis, album art, progress bar + butang prev / play-pause / next |
| Offline | Waktu solat sebulan & cuaca terakhir disimpan dalam phone |
| Privasi | Tiada iklan, tiada server, tiada akaun. Semua data kekal dalam phone |

## Syarat

- Phone Android **12 ke atas**
- Kereta / head unit yang support **Android Auto** (wired atau wireless)

## Cara install

1. Pergi ke tab **Releases** repo ini dan download fail `AutoTainment-v0.1.x.apk` terbaru dari phone.
2. Buka fail tu dan benarkan "Install unknown apps" bila diminta.
3. Buka app **AutoTainment** dalam phone dan lengkapkan semua kebenaran (lokasi, akses notifikasi, bateri).
4. Aktifkan **Unknown sources** dalam Android Auto:
   - Tetapan phone → cari **Android Auto**
   - Tekan **Version** 10 kali → OK
   - Menu ⋮ → **Developer settings** → tick **Unknown sources**
   - Android Auto → **Customise launcher** → tick **AutoTainment**
5. Sambung ke kereta. Ikon **AutoTainment** akan muncul dalam senarai app Android Auto.

**Xiaomi / POCO (HyperOS):** Tetapan app → **Autostart = ON**, **Penjimat bateri = Tiada sekatan**.

## Update

Setiap push ke `main` akan auto-build APK baru (GitHub Actions) dan terbit di Releases. Semua build ditandatangan dengan kunci yang sama (`app/debug.keystore`), jadi install terus atas versi lama tanpa uninstall.

> Kunci dalam repo ini ialah kunci debug untuk kegunaan peribadi. Jangan guna untuk terbitan Play Store.

## Peta jalan

- **v0.2** — YouTube (parking sahaja, video dikunci bila bergerak)
- **v0.3** — Gauge OBD-II live (boost, coolant, voltan) via ELM327 Bluetooth
- **v0.4** — Lirik lagu, harga minyak mingguan, simpan lokasi parking, trip log

## Keselamatan

Jangan berinteraksi dengan skrin semasa memandu. Ciri video (akan datang) dikunci bila kereta bergerak.

## Sumber data

- Waktu solat: [JAKIM e-Solat](https://www.e-solat.gov.my) melalui [api.waktusolat.app](https://api.waktusolat.app)
- Cuaca: [Open-Meteo](https://open-meteo.com)

App ini tidak berkaitan dengan Google, Honda, JAKIM atau mana-mana jenama.
