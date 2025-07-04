# ObsidianCalendarHelper
Helper app for the [Full Calendar](https://github.com/obsidian-community/obsidian-full-calendar?tab=readme-ov-file) plugin for [Obsidian.md](https://obsidian.md/), to generate native notifications for events and tasks.

# State of this project
This is a barebones start to a helper app that I vibe coded with Gemini in Android Studio. It's a start, but that's it, it still needs to actually parse the file format Full Calendar uses, it still needs to have a service that periodically checks for updates. I'd love to be able to use obsidian to manage my calendar, but I need notifications for events and tasks, which Obsidian doesn't allow plugins to do, so it would have to be implemented with a helper app that reads the files, parses the events, and generates notifications for those events. Ideally task notifications would allow you to mark them completed from the notification. Also, if an event gets deleted, the app shouldn't show notifications for it. 

I won't have time to finish this, so if anyone wants to fork it and keep working please do. Full Calendar also seems to be unmaintained, so working on that would be good too.
