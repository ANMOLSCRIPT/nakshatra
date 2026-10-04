# Heritage photographs (optional)

The website draws every card with procedural line art (`frontend/js/art.js`), so
it runs with no image files at all.

To show a photograph on a topic's card instead:

1. Put the file here, e.g. `konark-sun-temple.jpg` (landscape, about 1200 px wide).
2. In `knowledge_base/heritage.json`, add `"image": "konark-sun-temple.jpg"` to
   that topic.
3. Reload the page. If the file fails to load, the card falls back to the line art.

Only use images you have the right to show (your own photographs, or openly
licensed ones with the attribution their licence requires), and record the
source and licence in this file:

| file | source | licence / attribution |
|------|--------|-----------------------|
|      |        |                       |
