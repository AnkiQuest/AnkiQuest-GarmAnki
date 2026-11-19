---
id: PC-004
slug: ankiquest-jako-druga-aplikacja-z-fiszkami
data: 2026-11-19
typ: inne
proponent: hill (fork AnkiQuest)
status: proponowana
projekt: garmanki
tytul: "AnkiQuest jako druga aplikacja z fiszkami obok AnkiDroid"
decyzja: "Companion zna zamkniętą listę dwóch aplikacji z FlashCardsContract: AnkiDroid (domyślna, bez pytania) i AnkiQuest (fork AnkiDroid, tylko po wyborze użytkownika). AnkiDroid jest sprawdzany wbudowanym kluczem wydań AnkiDroid, wybór AnkiQuest jest przypięty do pakietu i SHA-256 certyfikatu podpisu."
dlaczego: "AnkiQuest ma ten sam kod providera pod własnym pakietem, authority i uprawnieniem, więc garmanki działa z nim bez zmian w protokole. Maintainer AnkiDroid sprzeciwia się otwartemu wykrywaniu providerów (aplikacje ufają, że rozmawiają z AnkiDroid), dlatego zmiana dodaje ochronę zamiast ją luzować: zamknięta lista, zgoda przez systemowe okno uprawnień, które samo nazywa aplikację, wbudowany klucz wydań AnkiDroid, który chroni też samych użytkowników AnkiDroid przed aplikacją zajmującą jego authority."
decyduje: "Jakub (user)"
tagi: [companion, ankidroid, ankiquest, bezpieczenstwo]
zastepuje: null
osiadlo: "android/…/anki/FlashcardApp.kt, ProviderCheck.kt, AndroidInstalledApps.kt, AndroidFlashcardApps.kt, AnkiDroidClient.kt, ekran główny (wybór „Flashcard app”), AndroidManifest.xml, SCHEMA.md §7, README.md"
---
# PC-004 · AnkiQuest jako druga aplikacja z fiszkami obok AnkiDroid

**Kontekst.** [AnkiQuest](https://github.com/AnkiQuest/AnkiQuest-Android) to fork AnkiDroid z tym samym ContentProviderem (`FlashCardsContract`), ale z pakietem `org.ankiquest.anki`, authority `org.ankiquest.anki.flashcards` i uprawnieniem `org.ankiquest.anki.permission.READ_WRITE_DATABASE`. Companion miał na sztywno `com.ichi2.anki`. Maintainer AnkiDroid sprzeciwił się otwartemu wykrywaniu providerów: „other apps expect only communicating to AnkiDroid because they trust us”.

**Decyzja.** Companion zna zamkniętą listę dwóch aplikacji z FlashCardsContract: AnkiDroid (domyślna, bez pytania) i AnkiQuest (tylko po wyborze użytkownika). AnkiDroid jest sprawdzany wbudowanym kluczem wydań, wybór AnkiQuest jest przypięty do pakietu i SHA-256 certyfikatu podpisu.

- Gdy AnkiDroid jest zainstalowany, zachowanie jest jak dotąd, bez pytania. Companion zna jego klucz wydań (Google Play, GitHub; SHA-256 `2071534F0F4B5E54AE952DD275D70DA6E3459EE69909D2AB1B4843C4C5B21A45`, wspólna kontrola w `anki/ProviderCheck.kt`): pasuje dowolny certyfikat z historii rotacji. AnkiDroid z innym kluczem nie jest używany („could not be verified”), chyba że sam companion jest debuggable (własna kompilacja AnkiDroid). Klucz AnkiQuest nie jest tu znany, więc AnkiQuest zostaje przy przypięciu przy wyborze.
- Wybór „Flashcard app” na ekranie głównym pokazuje tylko zainstalowane aplikacje z listy (nazwa i ikona), gdy AnkiDroid nie jest zainstalowany albo gdy są obie. Wybór od razu prosi o uprawnienie tej aplikacji; systemowe okno samo podaje jej nazwę.
- Przed każdym wywołaniem companion sprawdza certyfikat i to, że authority należy do oczekiwanego pakietu (`resolveContentProvider`). Zmieniony certyfikat AnkiQuest albo cudza aplikacja na authority: wybór jest kasowany i companion pyta ponownie. Odinstalowany wybór: powrót do AnkiDroid.
- Zegarek bez zmian: z providerem rozmawia tylko companion, protokół BLE (SCHEMA.md §1–§5, §8) się nie zmienia.

**Rozważane alternatywy.** Przypinanie certyfikatu AnkiDroid przy pierwszym użyciu (trust on first use) — zastąpione wbudowanym kluczem wydań: chroni też przy pierwszym uruchomieniu, gdy authority zajęła inna aplikacja. Otwarte wykrywanie każdej aplikacji z providerem `*.flashcards` — odrzucone (sprzeciw maintainera AnkiDroid, ryzyko podszycia). Automatyczne przełączanie na AnkiQuest, gdy AnkiDroid nie jest zainstalowany — odrzucone: companion nigdy nie zmienia aplikacji po cichu.

**Uzasadnienie.** AnkiQuest działa z garmanki bez zmian w protokole. Zamknięta lista, zgoda przez systemowe okno uprawnień, wbudowany klucz wydań AnkiDroid i przypięty certyfikat AnkiQuest dodają ochronę, także dla użytkowników samego AnkiDroid.

**Konsekwencje / gdzie osiadło.** AnkiDroid z F-Droid jest podpisany kluczem F-Droid, nie kluczem wydań, i nie przechodzi kontroli, dopóki jego SHA-256 nie zostanie wbudowany obok klucza wydań. android/…/anki/FlashcardApp.kt, ProviderCheck.kt, AndroidInstalledApps.kt, AndroidFlashcardApps.kt, AnkiDroidClient.kt, ekran główny (wybór „Flashcard app”), AndroidManifest.xml, SCHEMA.md §7, README.md.
