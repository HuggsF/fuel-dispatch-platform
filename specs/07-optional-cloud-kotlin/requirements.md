# Phase 07 — Optional: cloud & Kotlin · Requirements

Status: draft (run `/spec-design 07` and `/spec-tasks 07` only if time allows)
Depends on: phase 06

## Goal

Stretch goals that cover the remaining job items: public cloud deployment and Kotlin.

## Requirements

### OPT-1 AWS deployment

- **OPT-1.1** THE SYSTEM SHALL describe infrastructure as code (Terraform or AWS CDK) for: ECR,
  ECS Fargate (or EKS), RDS PostgreSQL, Amazon MSK Serverless (or Confluent Cloud), MongoDB Atlas
  (or DocumentDB).
- **OPT-1.2** WHEN the `deploy-aws` workflow runs manually THE SYSTEM SHALL deploy the SHA-tagged
  images and print the public URL.
- **OPT-1.3** THE SYSTEM SHALL document monthly cost and a `destroy` command, so the environment is
  only up during demos.
- **OPT-1.4** THE SYSTEM SHALL send logs and metrics to CloudWatch.

### OPT-2 Kotlin

- **OPT-2.1** THE SYSTEM SHALL provide a Kotlin variant of one tracking-service component (for
  example the Kafka listener and application service with coroutines) with the same tests passing,
  OR a small third service (`notification-service`) in Kotlin consuming the same topic.
- **OPT-2.2** THE SYSTEM SHALL record the comparison Java vs Kotlin in an ADR.

### OPT-3 Portability (optional)

- **OPT-3.1** THE SYSTEM SHALL provide a `.gitlab-ci.yml` equivalent to the GitHub pipeline.

## Out of scope

Multi-region, production SLAs.
