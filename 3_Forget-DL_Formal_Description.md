# Forget-DL: A Defeasible Semantics for Declarative and Procedural Forgetting in Mutation-Based Ceremony Verification

*Formal description of the system as implemented in X-Men 2.0 (`com.xmen.service.forget.dl`). Version of 6 October 2026.*

This document is written to be lifted into the paper. It fixes the definitions, states every result with its status, and separates what is proved, what is tested, and what is still open. Results marked **Proved** carry a proof here. Results marked **Open** must not be claimed as theorems. The notation follows the manuscript section `7.DefeasibleLogic.corrected.tex`. Where the implementation departs from or makes precise a manuscript definition, the departure is stated explicitly.

---

## 1. Overview

X-Men analyses a *security ceremony*: a protocol whose roles include a human agent $H$. The ceremony is a Tamarin multiset-rewriting theory. Human errors are modelled by *mutating* the role script before verification, so that an unmodified verifier analyses the mutated theory. The **Forget** mutation models memory lapses.

Forget-DL extends Forget along two axes:

1. **Procedural forgetting.** Besides forgetting a *value* (declarative forgetting), the human can forget an *inference rule* everywhere (for example symmetric decryption), or one *specific derivation* as a whole.
2. **Cue-triggered recovery.** A cue present in the environment can make a forgotten value accessible again at a single transition, without erasing the lapse.

The obstacle is that Tamarin resolves an honest agent's Dolev–Yao steps implicitly, through pattern matching. These steps are not rule applications in the specification, so they cannot be deleted from it. Forget-DL therefore decides, *outside* the verifier and for each human transition, which of the existing mutations (Preserve, Replace, Skip, Neglect) the cognitive state entails. It then rewrites the role script accordingly.

The decision is computed by compiling the human's state into a propositional theory of the ambiguity-blocking Defeasible Logic DL(∂) and reading the tags of a distinguished atom $\mathit{Usable}(t)$ for each candidate message $t$. The theory also yields an explanation trace.

The pipeline for each human transition $i \to i+1$ is:

$$
\sigma_i \xrightarrow{\ \text{state update}\ } \sigma_{i+1}
\xrightarrow{\ \text{derivations, candidates}\ } (T,\Pi)
\xrightarrow{\ \mathsf{compile}\ } D
\xrightarrow{\ \text{DL}(\partial)\ } \text{tags}
\xrightarrow{\ \text{decide}\ } \delta
\xrightarrow{\ \text{rewrite, certify}\ } \text{mutated rule(s)}.
$$

---

## 2. Preliminaries

### 2.1 Messages

Let $\mathcal A$ be a set of atoms: names, fresh values and public constants. Let $\Sigma_{\mathrm{pub}}$ be a set of public function symbols with arities, and $\Sigma_{\mathrm{priv}}$ a set of private ones. The set $\mathcal M$ of messages is the least set containing $\mathcal A$ and closed under:

- pairing $\langle a,b\rangle$;
- symmetric encryption $\mathsf{senc}(m,k)$;
- asymmetric encryption $\mathsf{aenc}(m,\mathsf{pk}(s))$;
- application $f(a_1,\dots,a_n)$ for $f \in \Sigma_{\mathrm{pub}} \cup \Sigma_{\mathrm{priv}}$.

Messages are interpreted in the free term algebra, so syntactic equality is message equality. Equational theories (XOR, Diffie–Hellman, bilinear pairings) and destructor symbols occurring inside terms are outside the supported fragment. The implementation rejects such inputs as `UNSUPPORTED`. $\mathrm{St}(X)$ denotes the subterms of a set $X$.

Each message $x$ has a **format**: $\mathit{format}(x) = \texttt{atom}$ if $x \in \mathcal A$, and the pair (top symbol, arity) otherwise. Messages may carry a **label** $\lambda(x)$, a type tag taken from the ceremony's `!Type` facts. $\lambda$ is a partial function.

> *Departure from the manuscript (A2).* The manuscript's Def. 3, read literally, sets the format of an atom to the atom itself. That reading forbids the manuscript's own password replacement $PW_1 \mapsto PW_2$. Both atoms must therefore share a format.

### 2.2 Inference rules and derivations

The set $\mathcal R$ of inference-rule labels contains:

| Label | Premises | Conclusion | Kind |
|---|---|---|---|
| $\mathsf{pair}$ | $a,\ b$ | $\langle a,b\rangle$ | constructor |
| $\mathsf{proj}$ | $\langle a,b\rangle$ | $a$ (resp. $b$) | destructor, inverse of $\mathsf{pair}$ |
| $\mathsf{senc}$ | $m,\ k$ | $\mathsf{senc}(m,k)$ | constructor |
| $\mathsf{sdec}$ | $\mathsf{senc}(m,k),\ k$ | $m$ | destructor, inverse of $\mathsf{senc}$ |
| $\mathsf{aenc}$ | $m,\ \mathsf{pk}(s)$ | $\mathsf{aenc}(m,\mathsf{pk}(s))$ | constructor |
| $\mathsf{adec}$ | $\mathsf{aenc}(m,\mathsf{pk}(s)),\ s$ | $m$ | destructor, inverse of $\mathsf{aenc}$ |
| $f \in \Sigma_{\mathrm{pub}}$ | $a_1,\dots,a_n$ | $f(a_1,\dots,a_n)$ | constructor |

The manuscript's label $\mathsf{unpair}$ is identified with $\mathsf{proj}$. The first premise of a destructor is its **principal premise**.

**Definition 1 (Derivation).** A *derivation* $\pi$ of $t$ from a finite set $K \subseteq \mathcal M$ is a finite tree, defined inductively:

- every leaf is $\mathsf{Initial}[x]$ with $x \in K$ and has conclusion $x$;
- every inner node applies a rule $r \in \mathcal R$ to the conclusions of its children;
- the root has conclusion $t$.

From a derivation we read off:

- the **hypotheses** $\mathit{Hyp}_\pi \subseteq K$, the set of leaf messages (for a decryption node, both the ciphertext and the key are premises, so both reach the hypotheses);
- the **rules used** $\mathit{Rules}(\pi) \subseteq \mathcal R$, the set of inner-node labels.

Derivations are identified up to structural equality of trees. The implementation names a derivation by $\mathit{id}(\pi)$, a SHA-256 hash of a structural encoding written `pi_<16 hex>`. Display strings play no part, so distinct terms with equal printed forms receive distinct identities (A13).

**Definition 2 (Normality, finite basis).**

- A derivation is **normal** if (N1) no destructor's principal premise is the conclusion of its inverse constructor, and (N2) no constructor is applied to premises that are all conclusions of its inverse destructor applied to one term. $\mathcal N_t(K)$ denotes the set of normal derivations of $t$ from $K$.
- The **finite basis** $\mathcal B^*_t(K) \subseteq \mathcal N_t(K)$ is the set of normal derivations in which no message occurs twice on any root-to-leaf branch.
- The **enumerated basis** $\mathcal B_t(K) \subseteq \mathcal B^*_t(K)$ is what the implementation returns. It is equal to $\mathcal B^*_t(K)$ unless a resource limit is hit (512 derivations per goal, 250 000 enumeration steps). The enumerator reports such truncation, and separately reports the presence of cyclic decryption dependencies.

**Remark 1 (correction of the manuscript, E3).** $\mathcal N_t(K)$ can be infinite. Take $K = \{\mathsf{senc}(a,b), \mathsf{senc}(b,a), a\}$. It has infinitely many normal derivations of $a$: decrypt $\mathsf{senc}(a,b)$ with $b$, which is obtained by decrypting $\mathsf{senc}(b,a)$ with $a$, and so on. By contrast, $\mathcal B^*_t(K)$ is finite. Under (N1)–(N2) every message on a branch lies in $\mathrm{St}(K \cup \{t\})$. Branch length is therefore bounded by $|\mathrm{St}(K\cup\{t\})|$, and branching is bounded by the maximal arity. The paper must not claim that "the space of normal derivations is finite".

### 2.3 Defeasible Logic DL(∂)

A defeasible theory is $D = (F, R, >)$:

- $F$ is a finite set of literals (facts);
- $R$ is a finite set of labelled rules $r : A(r) \hookrightarrow C(r)$, where $\hookrightarrow$ is strict ($\to$), defeasible ($\Rightarrow$) or a defeater ($\leadsto$);
- $>$ is an acyclic superiority relation on rules with complementary heads.

We write $R[q]$ for the rules with head $q$, $R_s[q]$ for the strict ones, $R_{sd}[q]$ for the strict and defeasible ones, and $\sim q$ for the complement of $q$. We use the proof theory of ambiguity-blocking DL(∂) with team defeat (Antoniou, Billington, Governatori and Maher, *ACM TOCL* 2001):

- $+\Delta q$ iff $q \in F$, or $\exists r \in R_s[q]\ \forall a \in A(r): +\Delta a$.
- $-\Delta q$ iff $q \notin F$ and $\forall r \in R_s[q]\ \exists a \in A(r): -\Delta a$.
- $+\partial q$ iff $+\Delta q$, or all of the following hold:
  - (2.1) $\exists r \in R_{sd}[q]\ \forall a \in A(r): +\partial a$;
  - (2.2) $-\Delta{\sim q}$;
  - (2.3) $\forall s \in R[{\sim q}]$: either (2.3.1) $\exists a \in A(s): -\partial a$, or (2.3.2) $\exists t \in R_{sd}[q]$ with $\forall a \in A(t): +\partial a$ and $t > s$.
- $-\partial q$ iff $-\Delta q$, and at least one of the following holds:
  - (2.1) $\forall r \in R_{sd}[q]\ \exists a \in A(r): -\partial a$;
  - (2.2) $+\Delta{\sim q}$;
  - (2.3) $\exists s \in R[{\sim q}]$ with (2.3.1) $\forall a \in A(s): +\partial a$ and (2.3.2) $\forall t \in R_{sd}[q]$: $\exists a \in A(t): -\partial a$ or $t \not> s$.

A rule is *applicable* when all its antecedents are $+\partial$, and *discarded* when some antecedent is $-\partial$. Three facts about this calculus are used below:

- a defeater never supports its head and never acts as a team member $t$ in (2.3.2);
- $-\partial q$ is a proved conclusion, distinct both from the failure to prove $+\partial q$ and from $+\partial{\sim q}$;
- no literal receives both $+\partial$ and $-\partial$ (coherence).

---

## 3. The cognitive state

**Definition 3 (Cognitive state).** The state of human $H$ at state index $i$ is

$$
\sigma_H^i = \bigl(K,\ \mathit{Forget},\ \mathit{PForget},\ \mathcal C\bigr),
$$

with the following components:

- $K = K_H^i \subseteq \mathcal M$ is the **knowledge**. It is monotone: forgetting never removes an element.
- $\mathit{Forget} \subseteq \mathcal M$ is the set of **declaratively forgotten** messages.
- $\mathit{PForget} = (\bar R, \bar\Pi)$ is the **procedural state**: $\bar R \subseteq \mathcal R$ are forgotten rules, and $\bar\Pi$ is a set of derivations forgotten as wholes.
- $\mathcal C = (\mathit{Cues}, \mathit{Assoc}, \mathit{Pres})$ is the **cue model** (manuscript Def. 8). $\mathit{Cues}$ is a finite set of cue identifiers, which are not messages. $\mathit{Assoc} \subseteq \mathit{Cues} \times K$ records which cue overlaps the encoding context of which message. $\mathit{Pres} \subseteq \mathit{Cues}$ is the set of cues present at this transition.

$\mathit{Assoc}$ persists across transitions. $\mathit{Pres}$ is local to one transition.

**Definition 4 (Blocking).** Fix a closure policy $\kappa \in \{1,2,3\}$. The blocking predicate $\beta^\kappa_{\mathit{Forget}}(x)$ holds iff:

- **Case 1:** $x \in \mathit{Forget}$;
- **Case 2:** $x$ is derivable from $\mathit{Forget}$ using only $\mathsf{pair}$ and $\mathsf{proj}$;
- **Case 3:** $x$ is derivable from $\mathit{Forget}$ using all supported constructors and destructors (private function symbols excluded).

For a set $X$, $\mathit{Blocked}(X, \mathit{Forget}) = \{x \in X \mid \beta^\kappa_{\mathit{Forget}}(x)\}$.

Cases 2 and 3 are decided by analysis/synthesis saturation. Saturation terminates because the analysed set stays within $\mathrm{St}(\mathit{Forget})$.

**Observation 1 (pointwise blocking).** $\beta^\kappa_{\mathit{Forget}}(x)$ depends only on $x$, $\mathit{Forget}$ and $\kappa$. It does not depend on the derivation or target in which $x$ occurs. Consequently $\mathit{Blocked}$ is monotone in $X$: $X \subseteq Y$ implies $\mathit{Blocked}(X,\mathit{Forget}) \subseteq \mathit{Blocked}(Y,\mathit{Forget})$.

**Definition 5 (Procedural blockers).** For a derivation $\pi$:

$$
\mathit{Blocked}_P(\pi) \;=\; \{\mathsf{Rule}(r) \mid r \in \mathit{Rules}(\pi) \cap \bar R\} \;\cup\; \{\mathsf{Proc}(\pi) \mid \pi \in \bar\Pi\}.
$$

**Definition 6 (Semantic usability).** Let $X_t$ be a set of derivations of $t$ from $K$. Write $\mathit{cued}(h)$ for $\exists c \in \mathit{Pres}: (c,h) \in \mathit{Assoc}$. A derivation $\pi$ **survives** in $\sigma$ iff

$$
\mathit{Blocked}_P(\pi) = \emptyset \quad\wedge\quad \forall h \in \mathit{Blocked}(\mathit{Hyp}_\pi, \mathit{Forget}):\ \mathit{cued}(h).
$$

Then $\mathsf U^{X}_\sigma(t) \iff \exists \pi \in X_t$ such that $\pi$ survives in $\sigma$. The reference semantics is $\mathsf U^{\mathcal N}_\sigma$, over all normal derivations. The implementation computes $\mathsf U^{\mathcal B}_\sigma$, over the enumerated basis (Theorem 1).

Definition 6 makes three modelling commitments explicit:

1. A cue restores a value only within the current transition. It modifies neither $K$ nor $\mathit{Forget}$.
2. A cue is message-specific: it never restores a forgotten rule or procedure.
3. A derivation blocked on several hypotheses needs a matching present cue for *each* of them.

Cue effectiveness is a modelling input: an ineffective cue is modelled by leaving it out of $\mathit{Pres}$ or $\mathit{Assoc}$. The model is a parameterised hypothesis supplied by the analyst, not a prediction of human behaviour.

---

## 4. Compilation into DL(∂)

Let $T$ be a finite set of candidate targets. For each $t \in T$, let $\Pi_t$ be the enumerated basis $\mathcal B_t(K)$. Let

$$
L \;=\; \bigcup_{t\in T}\ \bigcup_{\pi\in\Pi_t} \mathit{Blocked}(\mathit{Hyp}_\pi, \mathit{Forget})
$$

be the set of lapsed hypotheses. The theory $D = \mathsf{compile}(\sigma, T) = (F, R, >)$ is built as follows. All atoms are ground and propositional; message and derivation arguments are structural names, not variables.

**Facts $F$.**

| Fact | Included iff |
|---|---|
| $\mathit{Known}(x)$ | $x \in K$ |
| $\mathit{Lapsed}(h)$ | $h \in L$ |
| $\mathit{Lapsed}(\mathsf{Rule}(r))$ | $r \in \bar R$ |
| $\mathit{Lapsed}(\mathsf{Proc}(\pi))$ | $\pi \in \bar\Pi$ |
| $\mathit{ContextCue}(c,h)$ | $c \in \mathit{Pres}$, $(c,h) \in \mathit{Assoc}$, $h \in L$ |

**Rules $R$.** For each $t \in T$ and each $\pi \in \Pi_t$:

| Label | Rule | Emitted for |
|---|---|---|
| $d_\pi$ | $\bigwedge_{h\in\mathit{Hyp}_\pi}\mathit{Known}(h) \Rightarrow \mathit{Via}(\pi,t)$ | once |
| $\mathit{use}_{\pi,t}$ | $\mathit{Via}(\pi,t) \to \mathit{Usable}(t)$ | once |
| $\omega_{\pi,h}$ | $\mathit{Lapsed}(h) \leadsto \neg\mathit{Via}(\pi,t)$ | each $h \in \mathit{Hyp}_\pi$ |
| $\omega^{\mathsf{rule}}_{\pi,r}$ | $\mathit{Lapsed}(\mathsf{Rule}(r)) \leadsto \neg\mathit{Via}(\pi,t)$ | each $r \in \mathit{Rules}(\pi)$ |
| $\omega^{\mathsf{proc}}_{\pi}$ | $\mathit{Lapsed}(\mathsf{Proc}(\pi)) \leadsto \neg\mathit{Via}(\pi,t)$ | once |
| $\rho_{\pi,h,c}$ | $\mathit{ContextCue}(c,h) \Rightarrow \mathit{Via}(\pi,t)$ | each $h \in \mathit{Blocked}(\mathit{Hyp}_\pi,\mathit{Forget})$ and $(c,h) \in \mathit{Assoc}$ |

**Superiority.** $\rho_{\pi,h,c} > \omega_{\pi,h}$ for every reinstater. For fidelity to the manuscript, $\omega_{\pi,h} > d_\pi$, $\omega^{\mathsf{rule}}_{\pi,r} > d_\pi$ and $\omega^{\mathsf{proc}}_\pi > d_\pi$ are also included. Proposition 3 shows that these last pairs are inert. No rule is superior to a procedural defeater.

The manuscript writes $\mathit{Via}(K,\pi,t)$. The knowledge argument is fixed within one theory, so we omit it.

---

## 5. Metatheory of the compiled theory

Throughout this section, $D = \mathsf{compile}(\sigma, T)$, and every $\pi \in \Pi_t$ is a derivation from $K$, so $\mathit{Hyp}_\pi \subseteq K$.

**Lemma 1 (Fact layer). Proved.** For every atom $a$ of the forms $\mathit{Known}(\cdot)$, $\mathit{Lapsed}(\cdot)$ and $\mathit{ContextCue}(\cdot,\cdot)$:

- if $a \in F$, then $+\Delta a$ and $+\partial a$;
- otherwise $-\Delta a$ and $-\partial a$.

The negated literals $\neg a$ always receive $-\Delta$ and $-\partial$.

*Proof.* No rule has a head of these forms or their complements. If $a \in F$, then $+\Delta a$ holds by the first clause and $+\partial a$ by clause (1). If $a \notin F$, then $-\Delta a$ holds because $R_s[a] = \emptyset$ makes the universal condition vacuous, and $-\partial a$ holds by (2.1), which is vacuous because $R_{sd}[a] = \emptyset$. No negative literal is a fact, so the same argument applies to every $\neg a$. ∎

**Lemma 2 (Per-derivation survival). Proved.** For every $t \in T$ and $\pi \in \Pi_t$:

- $D \vdash +\partial\,\mathit{Via}(\pi,t)$ iff $\pi$ survives in $\sigma$ (Definition 6);
- otherwise $D \vdash -\partial\,\mathit{Via}(\pi,t)$.

Also, $-\partial\,\neg\mathit{Via}(\pi,t)$ always holds.

*Proof.* Neither $\mathit{Via}(\pi,t)$ nor its complement is a fact or the head of a strict rule, so $-\Delta\,\mathit{Via}(\pi,t)$ and $-\Delta\,\neg\mathit{Via}(\pi,t)$. $R_{sd}[\neg\mathit{Via}(\pi,t)] = \emptyset$, because all its rules are defeaters, so $-\partial\,\neg\mathit{Via}(\pi,t)$ by (2.1).

The supporters of $q = \mathit{Via}(\pi,t)$ are $d_\pi$ and the reinstaters $\rho_{\pi,h,c}$. By Lemma 1 and $\mathit{Hyp}_\pi \subseteq K$, $d_\pi$ is always applicable, so (2.1) of $+\partial$ holds. Condition (2.2) holds as shown above.

The attackers are the defeaters listed in §4. Their applicability is as follows:

- (a) $\omega_{\pi,h}$ is applicable iff $h \in L$. For $h \in \mathit{Hyp}_\pi$, $h \in L$ iff $h \in \mathit{Blocked}(\mathit{Hyp}_\pi,\mathit{Forget})$. The "if" direction holds by the definition of $L$. For "only if", $h \in L$ gives $\beta(h)$, and Observation 1 then puts $h$ in $\mathit{Blocked}(\mathit{Hyp}_\pi,\mathit{Forget})$.
- (b) $\omega^{\mathsf{rule}}_{\pi,r}$ is applicable iff $r \in \bar R$.
- (c) $\omega^{\mathsf{proc}}_\pi$ is applicable iff $\pi \in \bar\Pi$.

The only superiority pairs $(t', s)$ with $t' \in R_{sd}[q]$ and $s$ an attacker of $q$ are $\rho_{\pi,h,c} > \omega_{\pi,h}$. The reinstater $\rho_{\pi,h,c}$ is applicable iff $\mathit{ContextCue}(c,h) \in F$. For $h \in \mathit{Blocked}(\mathit{Hyp}_\pi,\mathit{Forget}) \subseteq L$, this holds iff $c \in \mathit{Pres}$ and $(c,h) \in \mathit{Assoc}$.

*(⇐)* Suppose $\pi$ survives. Consider an attacker $s$ of $q$:

- $s = \omega_{\pi,h}$ with $h$ unblocked: $s$ is discarded by (a), so (2.3.1) holds.
- $s = \omega_{\pi,h}$ with $h$ blocked: since $\mathit{cued}(h)$, some $\rho_{\pi,h,c}$ is applicable and superior to $s$, so (2.3.2) holds.
- $s$ procedural: $s$ is discarded by (b) and (c), since $\mathit{Blocked}_P(\pi) = \emptyset$.

Hence $+\partial q$.

*(⇒, contrapositive)* Suppose $\pi$ does not survive. Then some attacker $s$ is applicable and no applicable supporter is superior to it:

- If $\mathit{Blocked}_P(\pi) \neq \emptyset$, take $s$ procedural. No rule is superior to it.
- Otherwise, take $s = \omega_{\pi,h}$ for a blocked, uncued $h$. Every rule superior to $s$ is some $\rho_{\pi,h,c}$, and every such rule is discarded.

So clause (2.3) of $-\partial$ holds, and with $-\Delta q$ this gives $-\partial q$. By coherence, $+\partial q$ fails. ∎

**Theorem 1 (Boolean characterisation; manuscript P6). Proved.** For every $t \in T$:

$$
D \vdash +\partial\,\mathit{Usable}(t) \iff \mathsf U^{\mathcal B}_\sigma(t),
\qquad
D \vdash -\partial\,\mathit{Usable}(t) \iff \neg\,\mathsf U^{\mathcal B}_\sigma(t).
$$

In particular, every $\mathit{Via}$ and $\mathit{Usable}$ atom receives exactly one of $+\partial$ and $-\partial$. If $\Pi_t = \emptyset$, then $-\partial\,\mathit{Usable}(t)$.

*Proof.* $\neg\mathit{Usable}(t)$ is neither a fact nor the head of any rule, so $-\Delta\,\neg\mathit{Usable}(t)$, and condition (2.3) of $+\partial$ is vacuous. $R_s[\mathit{Usable}(t)] = \{\mathit{use}_{\pi,t}\}_{\pi\in\Pi_t}$, and every body has $-\Delta$, so $-\Delta\,\mathit{Usable}(t)$. Hence:

- $+\partial\,\mathit{Usable}(t)$ iff some $\mathit{Via}(\pi,t)$ has $+\partial$ (clause 2.1);
- $-\partial\,\mathit{Usable}(t)$ iff every $\mathit{Via}(\pi,t)$ has $-\partial$ (clause 2.1).

Lemma 2 concludes. ∎

**Corollary 1 (Per-hypothesis recovery; manuscript Lemma 1). Proved.** Suppose $\mathit{Blocked}_P(\pi) = \emptyset$. Then $+\partial\,\mathit{Via}(\pi,t)$ iff every blocked hypothesis of $\pi$ has a present associated cue. A cue associated with a different hypothesis does not help. *Proof.* Immediate from Lemma 2. ∎

**Proposition 1 (Joint compilation equals separate compilation; manuscript P2). Proved.** For $T' \subseteq T$ and $t \in T'$, the tags of $\mathit{Via}(\pi,t)$ and $\mathit{Usable}(t)$ in $\mathsf{compile}(\sigma,T')$ and in $\mathsf{compile}(\sigma,T)$ coincide. *Proof.* By Lemma 2 and Theorem 1, these tags are determined by $\pi$ and $\sigma$ alone. $T$ does not occur in the survival condition. ∎

The implementation therefore compiles a single theory for all candidates without cross-target interference.

**Proposition 2 (Monotonicity and conservativity; manuscript P3). Proved.** $\mathsf U^{X}_\sigma(t)$ behaves as follows:

- it is monotone non-decreasing in $\mathit{Pres}$ and $\mathit{Assoc}$;
- it is monotone non-increasing in $\bar R$ and $\bar\Pi$;
- for every $\kappa$, it is monotone non-increasing in $\mathit{Forget}$, since $\beta^\kappa_{\mathit{Forget}}$ is monotone in $\mathit{Forget}$.

In particular, adding a pair $(c,h)$ to $\mathit{Assoc}$, or $c$ to $\mathit{Pres}$, does not change $\mathsf U(t)$ when $h$ is not a blocked hypothesis of any derivation in $X_t$. *Proof.* Each conjunct of Definition 6 is monotone in the stated direction. ∎

A consequence for modelling attacker-controlled cues: for a fixed state and derivation set, presenting *additional* cues can never disable a surviving derivation. A cue alone therefore cannot cause a redirection. Wrong recall must be modelled through the association relation or the candidate set.

**Proposition 3 (The pairs $\omega > d$ are inert; erratum E4). Proved.** Removing the pairs $\omega_{\pi,h} > d_\pi$, $\omega^{\mathsf{rule}}_{\pi,r} > d_\pi$ and $\omega^{\mathsf{proc}}_\pi > d_\pi$ from $>$ changes no tag. *Proof.* The proofs of Lemma 2 and Theorem 1 use only superiority pairs whose left element is in $R_{sd}[\mathit{Via}(\pi,t)]$. Defeaters are never in $R_{sd}$. The left elements of the removed pairs are defeaters, and $R_{sd}[\neg\mathit{Via}] = \emptyset$. ∎

The manuscript's statement that "$\omega$, being superior to $d_\pi$, defeats $d_\pi$" should be rephrased. In DL(∂), an applicable defeater already blocks $+\partial$ of an unopposed supporter. The priorities that matter are that $d_\pi > \omega$ is absent and that $\rho > \omega$ is present.

**Proposition 4 (Agreement with the message-level semantics; manuscript P4). Proved, relative to a common derivation set.** Assume $\bar R = \bar\Pi = \emptyset$ and $\mathit{Pres} = \emptyset$. Then $\mathsf U^X_\sigma(t)$ holds iff some $\pi \in X_t$ has $\mathit{Blocked}(\mathit{Hyp}_\pi,\mathit{Forget}) = \emptyset$. This is the derivability-under-forgetting test of Algorithm 1, evaluated on the same derivation set $X$.

*Scope note.* The regression test `agreesWithAlgorithm1` checks this identity using the *new* enumerator for both sides. It is not a differential comparison with the legacy Algorithm 1 implementation, which has known derivation defects (D1–D10 in the semantic contract).

**Proposition 5 (Procedural expressiveness separation; manuscript P5). Proved.** Fix Case 1. Let $K = \{\mathsf{senc}(m_1,m_2),\ m_2\}$, let $\pi$ be the derivation $\mathsf{sdec}(\mathsf{Initial}[\mathsf{senc}(m_1,m_2)], \mathsf{Initial}[m_2])$ of $m_1$, and let $\sigma = (K, \emptyset, (\emptyset,\{\pi\}), \mathcal C_\emptyset)$. Then:

$$
\mathsf U^{\mathcal N}_\sigma(m_1) = \mathit{false}, \qquad \mathsf U^{\mathcal N}_\sigma(\mathsf{senc}(m_1,m_2)) = \mathsf U^{\mathcal N}_\sigma(m_2) = \mathit{true}.
$$

No state $\sigma' = (K, \mathit{Forget}', (\emptyset,\emptyset), \mathcal C_\emptyset)$ has the same usability on these three targets.

*Proof.* For $\sigma$: every normal derivation of $m_1$ from $K$ must decrypt $\mathsf{senc}(m_1,m_2)$, and in this $K$ the key $m_2$ is available only as a leaf. Hence $\pi$ is the unique normal derivation of $m_1$ in $\mathcal N_{m_1}(K)$, and it is procedurally blocked. The direct derivations of the other two targets are not in $\bar\Pi$ and have no blocked hypotheses.

For $\sigma'$: if neither hypothesis of $\pi$ is in $\mathit{Forget}'$, then $\pi$ survives and $m_1$ is usable. Otherwise some $x \in \{\mathsf{senc}(m_1,m_2), m_2\}$ is in $\mathit{Forget}'$. Every derivation of $x$ from $K$ has $x$ among its hypotheses: $m_2$ cannot be obtained by decryption from $K$, and constructing $\mathsf{senc}(m_1,m_2)$ requires $m_1$, which requires $\mathsf{senc}(m_1,m_2)$ as a hypothesis. So $x$ is unusable. ∎

The separation is relative to a fixed $K$, closure policy and set of observed targets. It does not claim that Tamarin, or a combination of other mutations, cannot express the resulting trace. The manuscript notes that the behaviour could be simulated by adding state facts to the ceremony.

**Remark 2 (Cue separation).** Consider two consecutive transitions with no lapse, relearning or receipt event between them. A cue $c$ with $(c,h) \in \mathit{Assoc}$ is present at the first transition and absent at the second, and $h \in \mathit{Forget}$. Then $h$ is usable at the first transition and unusable at the second, while $K$ and $\mathit{Forget}$ are unchanged. Under the message-level semantics, usability is a function of $(K, \mathit{Forget}, \kappa)$, and $\mathit{Forget}$ changes only at lapse or re-acquisition events. That semantics therefore cannot reproduce this observation sequence.

---

## 6. Algorithm 2: the per-transition decision

### 6.1 Input

A human transition $\tau$ has the form

$$
\mathsf{AgSt}(H,i,K^i),\ \mathit{Pre}_i,\ \mathsf{rcv}(m_1) \xrightarrow{\,a\,} \mathsf{AgSt}(H,i+1,K^{i+1}),\ \mathit{Post}_{i+1},\ \mathsf{snd}(m_2).
$$

Here $m_1$ or $m_2$ may be absent. $a$ is a set of action facts containing internal actions $\alpha$, each with an operand set $\mathit{Op}(\alpha)$. The transition also carries:

- a sequence of lapse events $\ell_1,\dots,\ell_k$, each a message $m$, a rule $r$ or a derivation $\pi$;
- relearning events for rules and procedures;
- cue declarations, associations and the presented cues $\mathit{Pres}^{i+1}$.

In the host these are the annotation facts `Forget(m)`, `ForgetRule('r')`, `ForgetProcedure('pi_…')`, `RelearnRule`, `RelearnProcedure`, `Cue`, `AssociateCue` and `PresentCue`. Labels come from `!Type(agent,'label',value)`.

### 6.2 State update

1. **Initialise.** $\mathit{Forget}' := \mathit{Forget}$, $(\bar R',\bar\Pi') := (\bar R,\bar\Pi)$, $K' := K \cup \{m_1\}$.
2. **Lapses, in order.**
   - **Message $m$:** $\mathit{Forget}' := \mathit{Forget}' \cup \{m\}$. If $m = m_1$, set $K' := K$: the received message is not retained.
   - **Rule $r$:** $\bar R' := \bar R' \cup \{r\}$.
   - **Procedure $\pi$:** $\bar\Pi' := \bar\Pi' \cup \{\pi\}$.

   (Erratum E8: the manuscript initialised $\mathit{Forget}^{i+1}$ with $m$ before testing whether the lapse is a message. The corrected order is used here.)
3. **Explicit relearning.** $\bar R' := \bar R' \setminus \bar R_{\mathrm{rel}}$ and $\bar\Pi' := \bar\Pi' \setminus \bar\Pi_{\mathrm{rel}}$.
4. **Neglect.** If a message forgotten in this transition occurs in $\mathit{Op}(\alpha)$ for an internal action $\alpha$, the decision is $\delta = \mathsf{Neglect}(\alpha)$ and the core returns (manuscript line 6). Steps 1–3 still apply; step 5 is skipped (A8, E7).
5. **Re-acquisition (fixpoint).** A message $f \in \mathit{Forget}'$ that was not lapsed in this transition is removed from $\mathit{Forget}'$ in either of two cases:
   - $f = m_1$ and $m_1$ is retained;
   - there is a derivation $\pi$ of $f$ from $K'$ with $m_1 \in \mathit{Hyp}_\pi$, $\mathit{Blocked}(\mathit{Hyp}_\pi,\mathit{Forget}') = \emptyset$ and $\mathit{Blocked}_P(\pi) = \emptyset$ under $(\bar R',\bar\Pi')$.

   This is iterated to a fixpoint (A6). Recovery derivations obey the current procedural state: a forgotten competence cannot be used to re-acquire.

The successor state is $\sigma_{i+1} = (K', \mathit{Forget}', (\bar R',\bar\Pi'), (\mathit{Cues}, \mathit{Assoc}', \mathit{Pres}^{i+1}))$.

### 6.3 Candidates and decision

6. **Candidate targets.** $T := \{m_2\}$. For each $\pi \in \Pi_{m_2}$ and each $\bar m \in \mathit{Blocked}(\mathit{Hyp}_\pi, \mathit{Forget}')$, let

   $$
   R_{\bar m} = \{\bar m' \in K' \setminus \mathit{Forget}' \mid \bar m' \neq \bar m,\ \mathit{format}(\bar m') = \mathit{format}(\bar m),\ \lambda(\bar m') = \lambda(\bar m)\}.
   $$

   If $\lambda(\bar m)$ is undefined, then $R_{\bar m} = \emptyset$ (A4). The condition $\bar m' \neq \bar m$ excludes self-replacement under Cases 2–3 (A5, E6). For every $C \in \prod_{\bar m} R_{\bar m}$, add $m_2[C]$, the simultaneous structural substitution of every occurrence, including inside function arguments (A12). The product is generated lazily, with at most 256 candidates and bounded work.
7. **Decide.** Let $D = \mathsf{compile}(\sigma_{i+1}, T)$. By Theorem 1:
   - if $\mathsf U(m_2)$: $\delta = \mathsf{Preserve}$;
   - otherwise, if $V = \{m' \in T \setminus \{m_2\} \mid \mathsf U(m')\} \neq \emptyset$: $\delta = \mathsf{Replace}(V)$, with one mutated rule per $m' \in V$;
   - otherwise: $\delta = \mathsf{Skip}$, subject to the coverage policy below.

   If there is no $m_2$, then $\delta = \mathsf{NoSend}$.
8. **Persist.** $\sigma_{i+1}$ becomes the predecessor state of the next transition of $H$. Later sends are re-evaluated under the persisting lapses even when they carry no annotation.

The intended send is preferred: a usable $m_2$ is never replaced. Replacement is generated only for *declarative* blockers. Substituting a hypothesis never restores a forgotten competence, because candidates are themselves subject to the procedural defeaters. The decision is complete *relative to $T$*: variants that keep one cue-recovered hypothesis and replace another are not generated.

### 6.4 Coverage and soundness of decisions

**Proposition 6 (Soundness of positive decisions). Proved.** $\mathsf U^{\mathcal B}_\sigma(t) \Rightarrow \mathsf U^{\mathcal N}_\sigma(t)$, for any truncation of the enumeration. *Proof.* $\mathcal B_t \subseteq \mathcal N_t$, and survival is a property of the individual derivation. ∎

A Preserve, or a Replace variant, justified by a witnessed surviving derivation therefore remains justified under the reference semantics, even when the enumeration was cut off.

**Proposition 7 (Completeness of negative decisions without exact-procedure lapses). Proved.** If $\bar\Pi = \emptyset$, then $\mathsf U^{\mathcal B^*}_\sigma(t) \iff \mathsf U^{\mathcal N}_\sigma(t)$.

*Proof.* (⇒) holds by inclusion. (⇐) Let $\pi \in \mathcal N_t$ survive. Apply the following reductions until none applies:

- (i) if a message $u$ occurs twice on a branch, replace the subtree at the upper occurrence by the subtree at the lower one;
- (ii) if a node violates (N1) or (N2) after (i), replace it by the subtree whose conclusion it recomputes.

In the free algebra, $\mathsf{sdec}(\mathsf{senc}(m,k),k)$ recomputes $m$, $\mathsf{senc}(\mathsf{sdec}(c,k),k)$ recomputes $c$, and similarly for pairs. Each step strictly decreases the number of nodes, so the process terminates. It preserves the root conclusion $t$ and only deletes nodes, so the result $\pi'$ is in $\mathcal B^*_t$ with $\mathit{Hyp}_{\pi'} \subseteq \mathit{Hyp}_\pi$ and $\mathit{Rules}(\pi') \subseteq \mathit{Rules}(\pi)$. By Observation 1, $\mathit{Blocked}(\mathit{Hyp}_{\pi'}) \subseteq \mathit{Blocked}(\mathit{Hyp}_\pi)$, and each of these hypotheses is still cued. Also $\mathit{Rules}(\pi') \cap \bar R = \emptyset$, and $\bar\Pi = \emptyset$. So $\pi'$ survives. ∎

**Remark 3 (Why exact-procedure lapses break completeness).** With $\bar\Pi \neq \emptyset$, the reduced $\pi'$ may itself be the forgotten procedure while $\pi$ is not. Example: $K = \{\mathsf{senc}(a,b), \mathsf{senc}(b,a), a\}$ and $\bar\Pi = \{\mathsf{Initial}[a]\}$. The cyclic tree $\mathsf{sdec}(\mathsf{senc}(a,b), \mathsf{sdec}(\mathsf{senc}(b,a), \mathsf{Initial}[a]))$ survives but lies outside $\mathcal B^*_a$. Cycles also affect re-acquisition: with old knowledge $\{a, h(a)\}$, forgotten $h(a)$ and receipt $\mathsf{senc}(a,a)$, the unblocked receipt-using proof $h(\mathsf{sdec}(\mathsf{senc}(a,a), a))$ is outside the basis.

**Coverage policy (A11).** The implementation acts on Propositions 6–7 as follows:

| Situation | Outcome |
|---|---|
| A witnessed surviving derivation of $m_2$, even under truncation | Preserve |
| $m_2$ not usable, and the basis is truncated, or exact-procedure lapses coexist with cyclic decryption dependencies | `UNKNOWN_COVERAGE`; no mutation is emitted |
| Re-acquisition search truncated or cyclic | `UNKNOWN_COVERAGE` for every outcome, because the persistent successor state is uncertain |
| Candidate or variant generation incomplete | Replace is emitted for the usable variants found, flagged `partial` |
| Skip | Only with complete coverage |
| Undecided literal (impossible for compiled theories by Theorem 1; guard only) | `UNDECIDED_LOGIC` |

**Open (manuscript P7, relative completeness).** Proposition 7 covers $\bar\Pi = \emptyset$. A full statement must characterise the cases with $\bar\Pi \neq \emptyset$ in which the implementation's acyclicity test makes $\mathcal B^*$ sufficient. In particular, it must establish that, under (N1)–(N2), a repeated message on a branch implies a cyclic decryption dependency of $K$. The same is needed for the re-acquisition search. Until this is proved, the paper should state Proposition 7 and the conservative policy, not a general completeness theorem.

---

## 7. From decisions to the mutated Tamarin model

### 7.1 Ordering and rewriting

The host orders each human's rules along a linear, acyclic `State(H, i, …)` chain, and along unambiguous directed four-argument channels. Terminal alternatives, such as `H_3_Granted` and `H_3_Denied`, are separate branches sharing the predecessor state. Native `Out`/`In` facts model an adversarial network: no exclusive sender is inferred. For each transition, the decision rewrites the rule as follows:

| $\delta$ | Rewriting |
|---|---|
| Preserve | Rule unchanged, except that annotations are stripped |
| Replace$(V)$ | One rule per $m' \in V$. The intended send is replaced structurally by $m'$. An atomic directed receive is rebound under a guard; compound or already-bound rebinding is rejected. |
| Skip | The send fact is removed |
| Neglect$(\alpha)$ | The internal action $\alpha$ is removed; the rest of the rule fires |
| NoSend | No send to decide; the state update persists |

### 7.2 Host certification

The core's Neglect return bypasses the send decision. In addition, Tamarin pattern matching could bind a forgotten value through any surviving premise or conclusion. The host therefore checks, after rewriting, that every surviving internal-check operand, operational output fact and co-send payload $x$ satisfies $\mathsf U_{\sigma_{i+1}}(x)$. A failure aborts the request (HTTP 400). Instrumentation facts must be declared through `Witness-Actions` so that they are not mistaken for checks. This check runs on symbolic terms, before any instantiation by the verifier.

### 7.3 What is and is not guaranteed

The results of §§5–6 concern the *decision*. They say nothing yet about the executions of the emitted theory. The intended end-to-end property can be stated as follows.

**Conjecture T1 (Translation soundness). Open.** Fix the supported fragment: free algebra, a linear State chain per human, restricted directed rebinding, and complete coverage. Then every trace of the mutated theory $\mu(M)$ projects onto a *cognitive execution* of $M$. That is, at every human step $j$, every value the step emits or checks is usable under $\sigma_j$, and the step's form agrees with the decision $\delta_j$.

Proving T1 requires the following:

- an execution semantics for the source ceremony annotated with cognitive state;
- a trace relation that accounts for the implicit operations in receive patterns, which are decryptions and projections performed by matching;
- invariance of the decision under the verifier's instantiation of variables and sessions.

Two remarks on the direction of the guarantee. Trace inclusion (T1) supports the validity of *reported attacks*. A claim that *all* cognitively permitted behaviours are analysed additionally needs the converse: completeness of candidate and mutation generation.

**Per-model validation (alternative to T1; partially demonstrated).** The emitted model can carry action facts:

- `Forgot(H,x)` at the lapse;
- `Uses(H,y)` for every value $y$ that a human rule emits or checks, extracted independently from the final rule text;
- exception facts `Reacquired(H,x)` and `Reinstated(H,x)`.

Tamarin then proves, over all traces, the following lemma:

```
lemma Certificate_No_Forgotten_Reuse:
  "All h x #i #j. Forgot(h,x) @ #i & Uses(h,x) @ #j & (#i < #j | #i = #j)
     ==> (Ex #k. Reacquired(h,x) @ #k & #i < #k & (#k < #j | #k = #j))
       | Reinstated(h,x) @ #j"
```

On the Bank model (single setup) with hand-written events, the correct translation verifies (149 steps) and a deliberately faulty translation, whose `Out` still sends the forgotten password, is falsified (7 steps).

Such a certificate establishes an *invariant of the emitted instrumentation*. It is not a source-to-target refinement. It does not detect:

- an omitted `Forgot` marker;
- a fabricated `Reacquired` or `Reinstated` event;
- a wrong but unforgotten replacement;
- an omitted permitted branch;
- uses of forgotten rules or procedures (unless a procedural extractor is added).

The trusted base therefore includes the source-state interpretation, the event extractor, the validation of recovery events and the verifier. The paper must state the certificate's guarantee in exactly these terms.

---

## 8. Implementation

### 8.1 Components

| Component | Responsibility | Approx. LOC |
|---|---|---|
| `Terms` | Canonical terms, subterms, format, structural substitution | 270 |
| `NormalDerivations` | $\mathcal B_t(K)$ with coverage flags; $\mathit{Hyp}_\pi$, $\mathit{Rules}(\pi)$, $\mathit{id}(\pi)$ | 330 |
| `ForgetBlocking` | $\mathit{Blocked}$, Cases 1–3 by saturation | 100 |
| `DefeasibleTheory`, `AmbiguityBlockingReasoner` | DL(∂) theory and reasoner | 510 |
| `ForgetDlCompiler` | §4 | 190 |
| `ForgetDlEngine`, `ForgetDlModel` | Algorithm 2 (§6), typed state | 630 |
| `ForgetDlResult` | Decision, candidates, cognitive trace | 210 |
| `ForgetDlBridge`, `ForgetMutationStrategy` | Host representation, ordering, rewriting, certification (§7) | — |

The core is pure Java with no dependency on Spring or Lombok. DL mode is selected per request (`Forget-Semantics: DL`) and does not alter the legacy Algorithm 1 path.

### 8.2 Reasoner

The reasoner is a self-contained implementation of the proof conditions of §2.3. It computes the least fixpoint of the four tag sets by iterating over all literals until nothing changes. The conditions are monotone in the tag sets, so the fixpoint contains exactly the conclusions with finite proofs. Literals on positive loops receive neither tag and are reported as undecided.

*Cost.* One round inspects, for each literal $q$, its supporters and, for each attacker, the supporters superior to it. A round therefore costs $O\bigl(\sum_q (|R[q]|\,b + |R[\sim q]|\,|R_{sd}[q]|\,b)\bigr)$, where $b$ bounds body size. For compiled theories, the rule-dependency depth is constant (facts, then $\mathit{Via}$, then $\mathit{Usable}$), so a constant number of rounds suffices. The cost is thus at most quadratic in $|D|$.

The linear-time bound known for propositional DL (Maher, 2001) holds for a different algorithm. **It must not be claimed for this implementation.** In practice, the cost is dominated by derivation enumeration and candidate generation, which are exponential in the worst case. Theorem 1 shows that a direct evaluation of Definition 6 would compute the same decision. The DL encoding is kept as a uniform specification and explanation layer: three blocker kinds, prioritised recovery and tagged justifications. It is not kept for reasoning power that the Boolean test lacks.

### 8.3 Cognitive trace

For each candidate and each derivation, the trace records the following:

- the tag of $\mathit{Via}$;
- $\mathit{Hyp}_\pi$, the blocked hypotheses and $\mathit{Rules}(\pi)$;
- the applicable supporting rules;
- every applicable defeater, classified as DECLARATIVE, RULE or PROCEDURE and marked *standing* or *overruled* (with the overruling reinstaters).

A Preserve is justified by a surviving $\mathit{Via}$, and if reinstated, by the $\rho$ that overruled $\omega$. A Replace is justified by standing defeaters on every derivation of $m_2$ together with a surviving derivation of the variant. A Skip is justified by standing defeaters on every derivation of every candidate, or by $\Pi = \emptyset$. The `pi_…` identifiers printed in the trace are those used in `ForgetProcedure` annotations.

### 8.4 Validation of the implementation

| Evidence | Result | Scope |
|---|---|---|
| Core test suite (Maven, JDK 21) | 85 tests, no failures | Manuscript Examples 1–10, Lemma 1, Tables 3–4, 4 × 400 seeded property scenarios, DL(∂) conformance |
| HTTP integration tests | 17 tests (latest full run 2 Oct 2026) | Later unannotated sends, procedural and cue annotations, directed channels, adversarial receives, unsafe-use rejection |
| Differential oracle against an independent ASP encoding of DL(∂) (clingo 5.8, seed 1) | 600 theories (400 random, 200 compiled), 14 034 literals, **0 mismatches** | The generator's fragment; the encoding's attacker binding is corrected |
| Same oracle, upstream encoding | 14 mismatches, all literals tagged both $+\partial$ and $-\partial$ | Reproduces a team-defeat binding defect in `gvdgdo/Defeasible-Deontic-Logic@679c638` |
| Earlier oracle run, seeds 1–5 | 3 000 theories, 69 066 literals, 0 mismatches | Historical; not rerun in the current review |

---

## 9. Assumptions and threats to validity

1. **Supported fragment.** Free constructors, pairs, symmetric and asymmetric encryption, public functions. No equational theories. A linear State chain per human. Unsupported inputs are rejected, never approximated.
2. **Human abstraction.** Inference rules abstract human-and-device competences. A human rarely decrypts by hand, so a rule lapse may stand for a forgotten device workflow. Lapses and cue associations are analyst-supplied hypotheses. The model does not predict behaviour.
3. **Cue provenance.** $\mathit{Pres}$ and $\mathit{Assoc}$ are fixed per scenario. Attacker-selected runtime cues are not modelled by the current compilation.
4. **Search limits.** Limits are coverage bounds, never evidence of absence. All negative outcomes under incomplete coverage are reported as unknown.
5. **Translation.** The connection between decisions and verifier executions is checked operationally (§7.2) and on fixtures. It is not established in general (Conjecture T1).

---

## Appendix A. Worked example (Invoice ceremony)

The payer $H$ has forgotten the supplier account $\mathit{acc}_S$. Both $\mathit{acc}_S$ and the attacker's $\mathit{acc}_{\mathit{Att}}$ carry the label `'account'`. The intended send is $m_2 = \mathsf{aenc}(\mathsf{sign}(\langle H, B, S, \mathit{service}, \mathit{amount}, \mathit{acc}_S\rangle, sk_H), pk_B)$.

The candidate set is $T = \{m_2, m_2[\mathit{acc}_S \mapsto \mathit{acc}_{\mathit{Att}}]\}$. For the unique basis derivation $\pi$ of $m_2$, the relevant part of the theory is:

```
Facts:  Known(acc_S), Known(sk_H), …, Lapsed(acc_S), ContextCue(independent, acc_S)
d:π                      Known(B) ∧ … ∧ Known(acc_S) ∧ Known(sk_H)  ⇒  Via(π, m2)
w:π:acc_S                Lapsed(acc_S)                             ⇝  ¬Via(π, m2)
rho:π:acc_S:independent  ContextCue(independent, acc_S)            ⇒  Via(π, m2)
use:π                    Via(π, m2) → Usable(m2)
Superiority: rho:π:acc_S:independent > w:π:acc_S
```

**With the cue present.** $\mathit{acc}_S$ is the only blocked hypothesis and it is cued, so Lemma 2 gives $+\partial\,\mathit{Via}(\pi,m_2)$. Theorem 1 then gives $+\partial\,\mathit{Usable}(m_2)$, and the decision is **Preserve**.

**Without the cue.** The fact $\mathit{ContextCue}$ is absent, so $\rho$ is discarded and $\omega$ stands. This gives $-\partial\,\mathit{Usable}(m_2)$. The variant has no blocked hypothesis, so the decision is **Replace** with $\mathit{acc}_{\mathit{Att}}$.

**With a signing-rule lapse** ($\bar R = \{\mathsf{sign}\}$). Every derivation of every candidate uses $\mathsf{sign}$. The cue, being message-specific, does not help. The decision is **Skip**.

In the generated Tamarin models, these three scenarios give the following results:

- *no cue:* a wrong-account trace is verified, and account safety is falsified;
- *cue present:* account safety is verified;
- *signing lapse:* safety holds only vacuously, because no send occurs.

The scenarios are minimal authored fixtures. They demonstrate the mechanism, not a complete payer/bank ceremony.
