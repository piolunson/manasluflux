# ManasluFlux — custom Discord presence name & logo

The **name** ("Meteor Client") and the **round logo** next to it come from the
*Discord Application* whose ID you put in the module's **application-id** setting —
they cannot be changed from the mod side. Make your own app once (2 minutes):

1. Open https://discord.com/developers/applications → **New Application**
2. Name it **Manaslu Flux**, upload [`manasluflux.png`](manasluflux.png) (512×512) as the app icon → this becomes your logo + name
3. Copy the **Application ID** (General Information; it's already copied by the button)
4. In game: Modules → Manaslu Flux → **Discord RPC** → Generic → `application-id` → paste it
5. Optional custom big image: RPC tab → **Art Assets** → upload anything ≤ 32 MiB, remember its *key*
   and put that key in the module's `image-name` setting (hover text = `image-text`,
   placeholders like `ManasluFlux {version}` work)

If the presence still shows "Meteor Client" for a moment — Discord caches the app
from the old ID; being on a server with the new ID propagated (a few minutes) fixes it.
