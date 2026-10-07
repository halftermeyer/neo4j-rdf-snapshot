# 06-closure-rule

The scope query returns a single relationship and no node.

Checks:

- both endpoints are added to the scope (closure rule, SPEC §3), with all their labels and properties;
- nodes not attached to an in-scope relationship (Bob) stay out.
