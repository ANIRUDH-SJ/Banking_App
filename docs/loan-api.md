# Loan information

All loan routes require a bearer JWT and scope results through the authenticated user's customer record.

| Method and path | Result |
| --- | --- |
| `GET /api/v1/loans` | Lists the customer's loans. |
| `GET /api/v1/loans/{loanId}` | Returns one owned loan. |

Responses include the original principal, current outstanding principal, rate, term, EMI, next due date,
maturity date, currency, and status. A loan owned by another customer is returned as not found.

Apply `database/12_loan_tables.sql` after the customer schema.
