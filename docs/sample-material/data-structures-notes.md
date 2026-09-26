# Data Structures — Course Notes

## 1. Arrays

An array stores elements of the same type in a contiguous block of memory. Because every element has the same
size, the address of element `i` is `base + i * size`, which is why reading or writing any index takes constant
time, O(1). The price of contiguity is that inserting or deleting in the middle requires shifting every later
element, which costs O(n).

A **dynamic array** (Python's `list`, Java's `ArrayList`) grows by allocating a larger block — typically double the
capacity — and copying the elements across. A single resize costs O(n), but because the capacity doubles, resizes
become rare: appending n elements costs O(n) in total, so each append is O(1) *amortized*.

Arrays have excellent cache locality: neighbouring elements sit in the same cache lines, so scanning an array is
much faster in practice than scanning a linked list with the same big-O complexity.

## 2. Linked lists

A singly linked list is a chain of nodes, each holding a value and a pointer to the next node. Inserting or
removing a node is O(1) once you hold a reference to its predecessor, because only pointers change. Finding the
k-th element, however, requires walking k nodes, so indexing is O(n). A doubly linked list adds a `prev` pointer,
which allows O(1) removal given only the node itself and makes backwards traversal possible, at the cost of extra
memory per node.

A common interview technique is the *two-pointer* or *fast/slow* pattern: advancing one pointer two steps for
every step of the other finds the middle of a list in one pass and detects cycles (Floyd's algorithm).

## 3. Stacks and queues

A **stack** is last-in, first-out (LIFO): `push` adds to the top and `pop` removes from the top, both O(1). Stacks
power function calls (the call stack), undo features, expression evaluation and depth-first search.

A **queue** is first-in, first-out (FIFO): `enqueue` adds at the back and `dequeue` removes from the front. A queue
implemented with a circular buffer or a linked list gives O(1) for both operations. Breadth-first search uses a
queue so that nodes are visited in order of their distance from the start.

## 4. Recursion

A recursive function solves a problem by calling itself on a smaller instance of the same problem. Every correct
recursive function needs a **base case** that is answered directly, without recursing; otherwise the calls never
stop and the program fails with a stack overflow. Each call gets its own stack frame, so recursion depth is
limited by the stack size — Python's default limit is about 1000 frames.

The factorial function illustrates the pattern: `factorial(0) = 1` is the base case and
`factorial(n) = n * factorial(n - 1)` is the recursive case. Naive recursive Fibonacci recomputes the same
subproblems exponentially many times; caching results (memoization) reduces it to linear time.

## 5. Binary trees and binary search trees

A binary tree is a set of nodes where each node has at most two children. The **height** of a tree is the number
of edges on the longest path from the root to a leaf. Tree traversals come in three depth-first orders — pre-order
(node, left, right), in-order (left, node, right) and post-order (left, right, node) — plus breadth-first level
order.

A **binary search tree (BST)** keeps every key in a node's left subtree smaller than the node and every key in the
right subtree larger. An in-order traversal of a BST therefore visits keys in sorted order. Search, insert and
delete take O(h) time where h is the height: O(log n) for a balanced tree but O(n) if keys arrive in sorted order
and the tree degenerates into a list. Self-balancing trees such as AVL trees and red-black trees perform rotations
to keep the height O(log n).

## 6. Heaps and priority queues

A **binary heap** is a complete binary tree — every level full except possibly the last, which fills from left to
right — that satisfies the heap property: in a min-heap every parent is less than or equal to its children, so the
minimum is always at the root. Because the tree is complete, a heap is stored compactly in an array: the children of
index `i` are at `2i + 1` and `2i + 2`, and its parent is at `(i - 1) // 2`.

Inserting appends the new element at the end and *sifts it up* while it is smaller than its parent: O(log n).
Removing the minimum moves the last element to the root and *sifts it down*: O(log n). Building a heap from n
unsorted elements with bottom-up heapify takes only O(n). Heaps implement priority queues, which are used by
Dijkstra's shortest-path algorithm, event schedulers and heapsort.

## 7. Hash tables

A hash table maps keys to values by computing a hash of the key and using it as an index into an array of buckets.
With a good hash function and a load factor kept below a threshold (commonly 0.75), lookups, inserts and deletes
take O(1) on average. Two keys that land in the same bucket are a **collision**; separate chaining stores a small
list per bucket, while open addressing probes for the next free slot. When the load factor grows too large the
table is resized and every key is rehashed, which is O(n) but amortized O(1) per insert.

## 8. Graphs

A graph is a set of vertices connected by edges, which may be directed or undirected and weighted or unweighted.
An **adjacency list** stores, for each vertex, the list of its neighbours and uses O(V + E) memory; an **adjacency
matrix** uses O(V²) memory but answers "is there an edge between u and v?" in O(1).

Breadth-first search explores vertices in order of distance and finds shortest paths in unweighted graphs.
Depth-first search goes as deep as possible before backtracking and is the basis of cycle detection and
**topological sorting**: an ordering of a directed acyclic graph in which every edge points from an earlier vertex to
a later one. Course prerequisites are a classic example — a valid study plan is a topological order of the
prerequisite graph, and a cycle in that graph means no valid plan exists.
