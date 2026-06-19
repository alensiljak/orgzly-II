import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";

   export default function (pi: ExtensionAPI) {
     const GIT = "/mnt/c/Users/alens/scoop/apps/git/2.54.0/bin/git.exe";

     pi.on("tool_call", async (event, ctx) => {
       if (event.toolName === "bash" && event.input.command?.startsWith("git ")) {
         event.input.command = event.input.command.replace(/^git /, `${GIT} `);
       }
     });
}