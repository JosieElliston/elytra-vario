> Well mapped at λ=0: collapse at 102–104, break-even dy=0 at n≈156, cyclic through 300. Above 300 only from the bad policy seed, so 400/500 need redoing under continuation.

Where dy crosses zero on the positive side — your original suggested upper bound.
Where the cycle stops being viable going negative — you predicted low n combined with heavy distance punishment, and that's now testable against a real collapse detector rather than a guess.
Stride — the README's family changed behavior near λ=0 (the dive stopped settling below w_old = −0.005, i.e. λ ≈ −0.96), which argues for finer spacing near zero.
My plan is to run λ under continuation at three n values spanning the regimes — one below break-even (n≈130), one at the reference (300), one above (450) — so the λ bounds are set across the n domain rather than only at 300, which is what you asked for originally.

Two things I'd rather you decide before I spend the compute:

λ range to probe. I'd sweep outward to λ = ±4 and let the collapse detector tell us where it dies, rather than assuming [−1, 1] holds. That's wider than your normalized [−1, 1] but it's the pilot's job to find the edges.
Whether n should extend past 500. Your original ask was 100–500, and dy is still climbing steeply at 300; if the interesting structure is the rate plateau, 500 may be short.

