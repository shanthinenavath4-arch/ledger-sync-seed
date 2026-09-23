# Task 1 — Track Teardown

## 1. Data Source Connection

I connected a supported financial data source to the Simplify Money app and used the Track feature to view transaction activity.

The purpose of this test was to understand how reliably the app detects, syncs, and presents financial transactions.

## 2. Transaction Tracking

After connecting the data source, I checked the synced transaction list and reviewed the transactions shown by the app.

The Track flow was useful for getting a consolidated view of financial activity, but transaction accuracy and categorization are important areas to verify before relying on the data for financial decisions.

## 3. Wrong or Missed Transactions

During testing, I identified transaction-level discrepancies between the source transaction information and what was displayed in the app.

### Issue 1 — Incorrect Transaction Information

The app displayed a transaction differently from the corresponding bank transaction information.

This matters because even a small amount mismatch can affect the user's understanding of their actual spending.

### Issue 2 — Missing / Incorrect Transaction

Another transaction was either not represented correctly or was missing from the tracked transaction list.

This matters because users need confidence that the complete transaction history is being captured.

## 4. Where I Trust the Product

I would trust the app for:

- Getting a consolidated overview of financial activity.
- Quickly reviewing recent transactions.
- Understanding overall spending activity when the underlying transactions have synced correctly.

## 5. Where I Would Not Fully Trust It Yet

I would verify:

- Exact transaction amounts against the original bank source.
- Transactions that appear missing or incorrectly categorized.
- Important financial decisions based on automatically detected transactions.

The main concern is not the tracking concept itself, but the possibility of an incorrect or incomplete transaction being presented as accurate.

## 6. Three Changes I Would Make

### 1. Add Better Transaction Verification

Show clearer transaction details and provide enough source information for users to verify an automatically detected transaction.

**Why:** This would make incorrect amounts or transaction details easier to identify.

### 2. Improve Missing-Transaction Detection

Add a mechanism to identify possible gaps between the connected source and the transactions shown in Track.

**Why:** Missing transactions can make spending totals inaccurate and reduce user trust.

### 3. Improve Transaction Categorization

Make the reason for a transaction category more transparent and allow users to correct incorrect categories easily.

**Why:** Clear categorization helps users understand their spending and prevents incorrect financial summaries.

## 7. Overall Observation

The Track feature provides a convenient way to bring financial transactions together in one place. However, transaction accuracy, completeness, and categorization are critical because users may rely on the resulting information to understand their finances.

My main takeaway from the test is that the product should make discrepancies easy to detect and correct while keeping the tracking experience simple.