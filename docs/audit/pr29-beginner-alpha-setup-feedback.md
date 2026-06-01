# PR #29 Beginner Alpha Setup Feedback

## Purpose

This documentation-only PR responds to first household tester feedback on the alpha setup guide.

The reviewer described the setup guide as hard to understand, "gibberish," and full of "jargon" for a non-programmer. They also pointed out that many general Minecraft players understand the word "mod," but the guide did not clearly explain whether TreasureRun is a plugin, a mod, a ResourcePack, or a combination of these pieces.

## Feedback summary

- The setup guide was too programmer-oriented.
- The guide did not quickly answer: "What am I testing?"
- The relationship between plugin, mod, and ResourcePack was unclear.
- The guide should use familiar Minecraft words before introducing architecture terms.
- The guide should make clear that the basic alpha starts with the local Spigot server plugin flow.

## Response

This PR updates `docs/external/ALPHA_TESTER_SETUP_GUIDE.md` by adding:

- a plain-English "Start here: what am I testing?" section;
- a simple plugin vs mod vs ResourcePack explanation;
- a clear note that TreasureRun is mainly a Spigot server plugin;
- a clear note that the Fabric mod is optional and only for advanced language-sync testing;
- simpler wording around starting the local server, connecting from Minecraft, and accepting the ResourcePack prompt.

## Boundaries

This PR does not:

- change gameplay behavior;
- change Java source code;
- change DB migrations;
- restart Docker;
- mutate GitHub Releases;
- publish to SpigotMC or Paper;
- claim Paper compatibility;
- claim production/live-server readiness.

## Career-facing value

This is a feedback-response PR: a real human tester found the onboarding confusing, and the project improved the documentation in response.

For career review, this demonstrates that TreasureRun is not only a technical implementation project, but also a contributor-facing OSS project that responds to human feedback.
