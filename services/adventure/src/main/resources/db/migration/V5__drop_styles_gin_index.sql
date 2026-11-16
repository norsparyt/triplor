-- The index study (docs/perf/phase-1-baseline.md) measured the search with and without this
-- index. With only six styles, a style filter matches a third to more than half of all rows,
-- so the index cannot narrow a search down; the planner used it anyway, and every measured
-- query was slower with it than without. It also had to be maintained on every write.
--
-- The location GiST index finds the candidate rows; the style filter is then checked on those.
DROP INDEX adventures_styles_gin;
