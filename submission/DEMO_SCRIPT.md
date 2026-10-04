# Demo video script — Nakshatra (SIH PS 214)

Target length: about 4 minutes. **Bold lines are what you say.** Lines in
*italics* are what to do on screen. Speak slowly; pause after each "Nakshatra"
until the page shows **Listening**.

## Before you press record

- Start the server: `python backend/cloud_server.py`
- Start the device: power the ESP32, or run `python hardware/edge_agent.py --device <idx>`
- Open http://localhost:8000 in Chrome, full screen. Click once anywhere on the
  page so the browser is allowed to speak. Check "Read answers aloud" is on and
  pick your voice.
- The top-right pill must say **Edge device connected**.
- Keep the speaker volume moderate and away from the microphone.

---

## 1. Opening (0:00 – 0:25)

*Show the hero section of the website.*

**Hello, we are team Nakshatra, and this is our solution for Smart India
Hackathon Problem Statement 214 — Cultural Heritage and Traditions of India.**

**India's heritage is huge: monuments, festivals, dance, music, crafts, food.
But most of it sits in text that people have to search for and read. We wanted
something simpler. You just ask a question with your voice, and you hear the
answer.**

## 2. What it is (0:25 – 0:50)

*Show the hardware on camera: the ESP32 and the INMP441 microphone. (If you are
recording with the laptop stand-in, say the second sentence in brackets instead.)*

**This is Nakshatra. It is a small voice-activated device built on an ESP32
with an INMP441 microphone, connected to this website.**

**[For this recording, the laptop microphone is standing in for the ESP32. It
runs the same wake-word model and the same protocol.]**

**The device listens for only one word — "Nakshatra". That detection happens on
the device itself, using a tiny neural network of about 45 kilobytes. Until it
hears its name, no audio is sent anywhere.**

## 3. How it works (0:50 – 1:20)

*Scroll to "How Nakshatra works". Point at the five steps as you name them.*

**The pipeline has five steps. One: the wake word is detected on the device.
Two: your question is streamed over Wi-Fi. Three: a speech recogniser on the
server converts it to text. Four: our answer engine matches it against a
cultural heritage knowledge base. Five: the answer is shown and spoken aloud.**

**So the edge does the cheap, private part, and the server does the heavy part.**

## 4. Live voice demo (1:20 – 2:40)

*Scroll to "Ask Nakshatra". Point at the status: "Say Nakshatra to begin".*

**Right now the device is idle. You can see the level meter moving, but only a
volume number is leaving the device — no audio.**

*Say clearly:* **"Nakshatra."** *Wait for "Nakshatra detected", then "Listening".*

*Then ask:* **"Why do we celebrate Diwali?"**

*Stay quiet while the answer is read. Then say:*

**Notice three things. The words appeared while I was still speaking. The
state moved from listening, to processing, to answering. And down here we show
the wake-word confidence and the time taken.**

*Second question. Say:* **"Nakshatra."** *Wait. Then:* **"Tell me about the Taj Mahal."**

*Let it answer.*

*Third question. Say:* **"Nakshatra."** *Wait. Then:* **"What is the classical dance of Tamil Nadu?"**

*Let it answer (Bharatanatyam). Then say:*

**I did not say the name of the dance. I only described it, and it still found
the right answer. The system matches by name, by description, and even by
sound when a word is mis-heard.**

## 5. What it does not do (2:40 – 3:05)

*Say a confusable word, without saying Nakshatra:* **"Natak."** *Pause.* **"Lakshan."**

**Those words sound similar, but the device did not wake up. We trained the
model with such confusing words on purpose.**

*Now say:* **"Nakshatra."** *Wait. Then:* **"What is the capital of France?"**

**That is outside our heritage archive, so it says it does not know, instead
of giving a wrong answer.**

## 6. The website (3:05 – 3:40)

*Scroll to "Explore India". Click "Festivals", then click one topic.*

**The website also works without voice. There are fifteen themes — architecture,
festivals, classical dance, music, textiles, cuisine, yoga and more — covering
one hundred and one topics.**

*Scroll to "Featured heritage", then to the map. Click one star.*

**These are featured heritage sites, and this is our heritage constellation:
seventy-eight places plotted at their real locations across India. Tap any
star to ask about it.**

*Click "Archive" in the menu. Type "silk" in the search box.*

**And the archive lists every question Nakshatra can answer, with search.
All of this comes from a single knowledge-base file, so adding a new topic
needs no code change.**

## 7. Closing (3:40 – 4:00)

*Go back to the hero section.*

**To summarise: the wake word runs on a low-cost microcontroller, audio is sent
only after the wake word, and the answers come from a curated, accurate
knowledge base. It can sit in a museum, a school, or a tourist kiosk.**

**Discover India, one question at a time. Thank you.**

---

## If something goes wrong while recording

| Problem | What to do |
|---|---|
| Wake word does not fire | Say it again at normal volume, about one metre from the mic. Do not rush. |
| A question is heard wrongly | Stop, say "Nakshatra" again and repeat. Or use one of the questions below. |
| No spoken answer | Click once on the page, check the "Read answers aloud" switch. |
| "Edge device offline" | Restart the device or `edge_agent.py`; only one can be connected. |

**Questions that were recognised correctly in testing** (use these as spares):
"What is the Red Fort?", "What is Navratri?", "What is yoga?", "What is the
festival of colours?", "Tell me about the Golden Temple", "What is Raksha
Bandhan?", "Why is Jaipur called the Pink City?", "Tell me about Konark Temple".
Avoid rare names on camera (Mohiniyattam, Dholavira, Bidriware); the speech
recogniser often mis-hears them.
