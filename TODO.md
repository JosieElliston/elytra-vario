# TODO

- should we expose rules for when to switch?
- bar graph for speed, energy

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

## batch

(this is elytrasim stuff, work in myopic-metrics, not elytra-vario)
(should be in american english)

i want to see how robust the myopic metrics (hold-angle during dive, 20-tick horizon during gain) are across near-optimal flight profiles under different constrains.

investigate the family of flight profiles where

under both time and distance constraints (the distance constraint by changing the utility function to a linear combination of energy and distance, not literally a constraint).

try stitching the dive and gain phases from different profiles. they should agree on the snap-to-0 phase, which i've empirically observed to fairly robustly be of constant duration ~10 ticks, so the stitch should be pretty clean. just use the flick-up phase from the same profile as the gain phase, it doesn't seem to matter that much (actually maybe it does matter if you consider distance? be aware).

also initial vel

also regularization

initial predive entry phase

also interested in what differs between the constraints. entry phase and flick up

write the sweep to find the optimal pitches, i'll run it on a cluster

num_ticks: in 100 to 500, stride 10
distance: idk
regularization: idk
