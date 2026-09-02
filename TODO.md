# TODO

- add draw_arrow_types for the hold flight-path angle

- should we expose rules for when to switch?

## myopic metrics

ok i want to do some elytrasim stuff, but i'll do it here bc you have context.

what myopic metrics does the global optimum piecewise agree with? here is a vague categorization of the phases.

- the first phase is getting to down/left velocity (note there's weirdness at the start i'm glossing over where it likes harshly pitching down).
- next is snapping to zero. we've investigated this, and it seems to be efficiently converting -y vel to +z vel, temporarily exceeding the ~terminal velocity, giving you a speed boost. (if you're going fast enough, you're also gaining TE, but this isn't the main contribution)
- next is the flick up to ~-90. idk how this works, and the precise values seem to not be important.
- finally is the gain phase, where you're pitching down to 0 from -90 and gaining TE. pitch seems to agree with the 1-tick argmax delta TE, which actually makes sense for the gain phase. (please check how well they agree)

it would be really cool to have bugs for simple myopic metrics that you can follow for each phase, and the only difficulty is timing switching between them. we have for the gain phase currently. ig we have one for the flick to 0 phase. i have the most hope for the dive phase (at least after the weird start).

here are some pitch profiles you can reference, but you can optimize your own too, but it's a bit fiddly.

- /Users/josie/Library/Application Support/ModrinthApp/profiles/main/minescript/pitches.py
- /Users/josie/programming_local/elytrasim-luna/src/replay_pitches.rs
