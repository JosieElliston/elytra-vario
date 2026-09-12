# TODO

josie's todo/notes, don't edit this, tho you should include it in commits

- should we expose rules for when to switch?
- rename vel graph to energy field
- some other rename i wanted?
- new module: the dxz, dy, dpe, dke, dte against pitch plots
- maybe remove true pos dnd outline? also nudge with wasd (or maybe player controls? but what if you have walk bound to left click? stuff like that is scary)
- energy grid contours for 0 and ridges
- better ladder marker aesthetics
    - configurable n:1 pixel step down
        - 1:1 pixel step down is a 90 deg point
        - 2:1 pixel step down is a 45 deg point
    - configurable length ("height")
        - aligned to outside?
        - absorb < step n at the base, not the tip
        - actually idk where you should absorb it, maybe this is configurable???
        - actually instead of height, it's the inset. so we have [start, end] rather than [start, length]? actually i don't like this.
    - configurable inset
    - configurable base radius (must have odd base size) actually no we don't have base radius, this is implied by length
        - lines are expressible as 0 rad and (special case i think) 0 step down
    - build a gui editor for this
- better color picker
- make modules more unobtrusive
    - translucent
- not fixed module render order?
- if you are dragging a corner, the center comes into radius before the edge, so the center is draw without the edge, so we sometimes snap to an edge without it getting drawn. ig this is correct tho.
- ehop/ebounce
    - should we use ehop or ebounce
    - number of ticks between last ground touch and last elytra deploy
    - ground touch
        - speed
    - last elytra deploy
        - speed
        - distance delta from ground touch
- sort the stats panel so it's speed/accel y, xz, xyz rather than xz, xyz, y
- make the panel hover text hide when dragging (not hovering) a panel. rn the hover text obscures elements to the right you might be trying to align to.

## myopic metrics

ok i want to do some elytrasim stuff, but i'll do it here bc you have context.

what myopic metrics does the global optimum piecewise agree with? here is a vague categorization of the phases.

- the first phase is getting to down/left velocity (note there's weirdness at the start i'm glossing over where it likes harshly pitching down).
- next is snapping to zero. we've investigated this, and it seems to be efficiently converting -y vel to +z vel, temporarily exceeding the ~terminal velocity, giving you a speed boost. (if you're going fast enough, you're also gaining TE, but this isn't the main contribution)
- next is the flick up to ~-90. idk how this works, and the precise values seem to not be important.
- finally is the gain phase, where you're pitching down to 0 from -90 and gaining TE. pitch seems to agree with the 1-tick argmax delta TE, which actually makes sense for the gain phase. (please check how well they agree)

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
