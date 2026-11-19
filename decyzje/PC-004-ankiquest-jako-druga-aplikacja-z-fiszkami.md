---
id: PC-004
slug: ankiquest-jako-druga-aplikacja-z-fiszkami
data: 2026-11-19
typ: inne
proponent: hill (fork AnkiQuest)
status: proponowana
projekt: garmanki
tytul: "AnkiQuest jako druga aplikacja z fiszkami obok AnkiDroid"
decyzja: "Companion zna zamkniętą listę dwóch aplikacji z FlashCardsContract: AnkiDroid (domyślna, bez pytania) i AnkiQuest (fork AnkiDroid, tylko po wyborze użytkownika). Wybór jest przypięty do pakietu i SHA-256 certyfikatu podpisu."
dlaczego: "AnkiQuest ma ten sam kod providera pod własnym pakietem, authority i uprawnieniem, więc garmanki działa z nim bez zmian w protokole. Maintainer AnkiDroid sprzeciwia się otwartemu wykrywaniu providerów (aplikacje ufają, że rozmawiają z AnkiDroid), dlatego zmiana dodaje ochronę zamiast ją luzować: zamknięta lista, zgoda przez systemowe okno uprawnień, które samo nazywa aplikację, i przypięty certyfikat, który chroni też AnkiDroid przed przejęciem authority."
decyduje: "Jakub (user)"
tagi: [companion, ankidroid, ankiquest, bezpieczenstwo]
zastepuje: null
osiadlo: "android/…/anki/FlashcardApp.kt, AndroidFlashcardApps.kt, AnkiDroidClient.kt, ekran główny (wybór „Flashcard app”), AndroidManifest.xml, SCHEMA.md §7, README.md"
---
# PC-004 · AnkiQuest jako druga aplikacja z fiszkami obok AnkiDroid

**Kontekst.** [AnkiQuest](https://github.com/AnkiQuest/AnkiQuest-Android) to fork AnkiDroid z tym samym ContentProviderem (`FlashCardsContract`), ale z pakietem `dev.hilll.anki`, authority `dev.hilll.anki.flashcards` i uprawnieniem `dev.hilll.anki.permission.READ_WRITE_DATABASE`. Companion miał na sztywno `com.ichi2.anki`. Maintainer AnkiDroid sprzeciwił się otwartemu wykrywaniu providerów: „other apps expect only communicating to AnkiDroid because they trust us”.

**Decyzja.** Companion zna zamkniętą listę dwóch aplikacji z FlashCardsContract: AnkiDroid (domyślna, bez pytania) i AnkiQuest (tylko po wyborze użytkownika). Wybór jest przypięty do pakietu i SHA-256 certyfikatu podpisu.

- Gdy AnkiDroid jest zainstalowany, zachowanie jest jak dotąd, bez pytania; jego certyfikat jest przypinany przy pierwszym użyciu.
- Wybór „Flashcard app” na ekranie głównym pokazuje tylko zainstalowane aplikacje z listy (nazwa i ikona), gdy AnkiDroid nie jest zainstalowany albo gdy są obie. Wybór od razu prosi o uprawnienie tej aplikacji; systemowe okno samo podaje jej nazwę.
- Przed każdym wywołaniem companion sprawdza certyfikat i to, że authority należy do oczekiwanego pakietu (`resolveContentProvider`). Zmieniony certyfikat albo cudza aplikacja na authority: wybór jest kasowany i companion pyta ponownie. Odinstalowany wybór: powrót do AnkiDroid.
- Zegarek bez zmian: z providerem rozmawia tylko companion, protokół BLE (SCHEMA.md §1–§5, §8) się nie zmienia.

**Rozważane alternatywy.** Otwarte wykrywanie każdej aplikacji z providerem `*.flashcards` — odrzucone (sprzeciw maintainera AnkiDroid, ryzyko podszycia). Automatyczne przełączanie na AnkiQuest, gdy AnkiDroid nie jest zainstalowany — odrzucone: companion nigdy nie zmienia aplikacji po cichu.

**Uzasadnienie.** AnkiQuest działa z garmanki bez zmian w protokole. Zamknięta lista, zgoda przez systemowe okno uprawnień i przypięty certyfikat dodają ochronę, także dla użytkowników samego AnkiDroid.

**Konsekwencje / gdzie osiadło.** android/…/anki/FlashcardApp.kt, AndroidFlashcardApps.kt, AnkiDroidClient.kt, ekran główny (wybór „Flashcard app”), AndroidManifest.xml, SCHEMA.md §7, README.md.
