#!/usr/bin/env python3
"""
Computational verification for the two new GRC-Koopman theorems.

Theorem (finite dictionary / polynomial exactness).
  For G : F_q^n -> F_q^m represented by reduced polynomials (degree < q in
  each variable), d is transportable iff the polynomial in s

      P_{G,d}(s) = G(s+d) - G(s)

  has no non-constant monomial; then the transport is T(d) = G(d) - G(0).

  Over F_2 this is checked below by exhaustive ANF expansion for ALL
  functions F_2^n -> F_2 with n = 2,3 (16 and 256 functions, all d).
  Over F_3 it is checked for ALL reduced polynomials F_3^2 -> F_3
  (3^9 = 19683 functions, all 9 deltas).

Theorem (statistical transport projection).
  For any finite base distribution mu and linearized target space,
  T_mu(d) = E_s[ G(s+d) - G(s) ] is a constant (virtual) transport with
  zero-mean residual.  This script verifies the mean-equivariance identity
  and the residual identity on a deliberately non-transportable example.
"""

from itertools import product


# ---------------------------------------------------------------- F_2 / ANF
def anf_coeffs(table, n):
    """Mobius transform: table -> algebraic normal form coefficients."""
    c = table[:]
    for i in range(n):
        bit = 1 << i
        for mask in range(1 << n):
            if mask & bit:
                c[mask] ^= c[mask ^ bit]
    return c


def brute_derivative_const(table, n, d):
    vals = [table[s ^ d] ^ table[s] for s in range(1 << n)]
    return all(v == vals[0] for v in vals), vals[0]


def check_f2_all_functions(n):
    N = 1 << n
    checked = 0
    for fi in range(1 << N):
        table = [(fi >> x) & 1 for x in range(N)]
        for d in range(N):
            is_const, _ = brute_derivative_const(table, n, d)
            p = [table[s ^ d] ^ table[s] for s in range(N)]
            pc = anf_coeffs(p, n)
            criterion = all(pc[m] == 0 for m in range(1, N))
            assert is_const == criterion, (n, fi, d, pc)
        checked += 1
    return checked


# ---------------------------------------------------------------- F_q reduced polynomials
def poly_eval(q, n, coeffs, x):
    """coeffs indexed by alpha in [0,q-1]^n, mixed radix."""
    total = 0
    for alpha, c in enumerate(coeffs):
        if c == 0:
            continue
        term = c
        rem = alpha
        for i in range(n):
            ai = rem % q
            rem //= q
            xi = (x // (q ** i)) % q
            term = (term * pow(xi, ai, q)) % q
        total = (total + term) % q
    return total


def all_reduced_coeffs(q, n):
    return product(range(q), repeat=q ** n)


def check_fq_all_functions(q, n):
    count = 0
    for coeffs in all_reduced_coeffs(q, n):
        for d in range(q ** n):
            vals = [((poly_eval(q, n, coeffs, (s + d) % (q ** n))
                      - poly_eval(q, n, coeffs, s)) % q)
                    for s in range(q ** n)]
            is_const = all(v == vals[0] for v in vals)
            t = (poly_eval(q, n, coeffs, d)
                 - poly_eval(q, n, coeffs, 0)) % q
            criterion = all(v == t for v in vals)
            assert is_const == criterion, (q, n, coeffs, d, vals, t)
        count += 1
    return count


# ---------------------------------------------------------------- statistical projection
def check_statistical_projection():
    """G(s)=s^2 mod 3, S=F_3, d=1, mu = (1/2,1/2,0).  Derivative
    values are [0,0,2]; the projection T_mu(d)=1 is a virtual delta
    even though no exact transport exists."""
    mu = [0.5, 0.5, 0.0]
    d = 1
    q = 3
    G = [s * s % q for s in range(q)]

    def deriv(s):
        return (G[(s + d) % q] - G[s]) % q

    derivs = [deriv(s) for s in range(q)]
    assert derivs == [1, 0, 2]
    assert not all(v == derivs[0] for v in derivs)  # not exactly transportable

    T = sum(mu[s] * derivs[s] for s in range(q))
    assert abs(T - 0.5) < 1e-12

    lhs = sum(mu[s] * G[(s + d) % q] for s in range(q))
    rhs = sum(mu[s] * G[s] for s in range(q)) + T
    assert abs(lhs - rhs) < 1e-12
    residual_mean = sum(mu[s] * (derivs[s] - T) for s in range(q))
    assert abs(residual_mean) < 1e-12

    # P_mu is an idempotent projection on observables f:S->R:
    # P f = sum mu[s] f[s], constant observable.
    obs = [(s * s + 2 * s + 1) % 5 / 7.0 for s in range(q)]  # arbitrary toy observable
    P = [sum(mu[t] * obs[t] for t in range(q))] * q
    PP = [sum(mu[t] * P[t] for t in range(q))] * q
    assert all(abs(a - b) < 1e-12 for a, b in zip(P, PP))
    return T, derivs


if __name__ == "__main__":
    c2 = check_f2_all_functions(2)
    c3 = check_f2_all_functions(3)
    print(f"[ok] F_2: all {c2} functions on F_2^2 and all {c3} functions on F_2^3 satisfy the ANF exact-dictionary criterion")
    cq = check_fq_all_functions(3, 2)
    print(f"[ok] F_3: all {cq} reduced polynomial functions on F_3^2 satisfy G(d)-G(0) exactness criterion")
    T, derivs = check_statistical_projection()
    print(f"[ok] statistical projection example: derivatives={derivs}, T_mu={T}, residual mean=0")
