package web5.sdk.credentials

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.Ed25519Signer
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import web5.sdk.crypto.AwsKeyManager
import web5.sdk.crypto.InMemoryKeyManager
import web5.sdk.dids.CreateDidIonOptions
import web5.sdk.dids.DidIonHandle
import web5.sdk.dids.DidIonManager
import web5.sdk.dids.DidKey
import web5.sdk.dids.ion.model.PublicKey
import java.security.SignatureException
import java.text.ParseException
import java.util.UUID
import kotlin.test.Ignore
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

data class StreetCredibility(val localRespect: String, val legit: Boolean)
class VerifiableCredentialTest {
  @Test
  @Ignore("Testing with a prev created ion did")
  fun `create a vc with a previously created DID in the key manager`() {
    val keyManager = AwsKeyManager()
    val didUri =
      "did:ion:EiCTb6TakNEaBkYK0ZVtCC26mdv8mGZ8Z7YnbsSf-kiMyg" +
        ":eyJkZWx0YSI6eyJwYXRjaGVzIjpbeyJhY3Rpb24iOiJyZXBsYWNlIiwiZG9jdW1lbnQiOnsicHVibGljS2V5cyI6W3siaWQiOiIwMzlhZTc" +
        "xYy04OTZjLTQ2MzgtYjA3My0zYTQyM2IwMjhiMDEiLCJwdWJsaWNLZXlKd2siOnsiYWxnIjoiRVMyNTZLIiwiY3J2Ijoic2VjcDI1NmsxIiw" +
        "ia2lkIjoiYWxpYXMvTzNmZUVhSDlaTVFmdkg3cTFkSUw3OFNxUmRJWkhnVUJlcFU3c1RtbHY1OCIsImt0eSI6IkVDIiwidXNlIjoic2lnIiw" +
        "ieCI6IllwbTNZWS1oVnNqWjV2ME83aGRhZS1WVi1DRm1Ib0hldWFZODAtV08wS0UiLCJ5IjoiUnU5QlA2RzctU0lxU3E0MFdUenk5MnpiWXd" +
        "aRHBuVmlDUWxRSHpNWVQzVSJ9LCJwdXJwb3NlcyI6WyJhc3NlcnRpb25NZXRob2QiXSwidHlwZSI6Ikpzb25XZWJLZXkyMDIwIn1dLCJzZXJ" +
        "2aWNlcyI6W119fV0sInVwZGF0ZUNvbW1pdG1lbnQiOiJFaUNsaVVIbHBQQjE0VVpkVzk4S250aG8zV2YxRjQxOU83cFhSMGhPeFAzRkNnIn0" +
        "sInN1ZmZpeERhdGEiOnsiZGVsdGFIYXNoIjoiRWlEU2FMNHZVNElzNmxDalp4YVp6Zl9lWFFMU3V5T3E5T0pNbVJHa2FFTzRCQSIsInJlY29" +
        "2ZXJ5Q29tbWl0bWVudCI6IkVpQzI0TFljVEdRN1JzaDdIRUl2TXQ0MGNGbmNhZGZReTdibDNoa3k0RkxUQ2cifX0"
    val issuerDid = DidIonHandle(didUri, keyManager)
    val holderDid = DidKey.create(keyManager)

    val vc = VerifiableCredential.create(
      type = "StreetCred",
      issuer = issuerDid.uri,
      subject = holderDid.uri,
      data = StreetCredibility(localRespect = "high", legit = true)
    )

    val vcJwt = vc.sign(issuerDid)

    assertDoesNotThrow {
      VerifiableCredential.verify(vcJwt)
    }
  }

  @Test
  fun `create works`() {
    val keyManager = InMemoryKeyManager()
    val issuerDid = DidKey.create(keyManager)
    val holderDid = DidKey.create(keyManager)

    val vc = VerifiableCredential.create(
      type = "StreetCred",
      issuer = issuerDid.uri,
      subject = holderDid.uri,
      data = StreetCredibility(localRespect = "high", legit = true)
    )
    assertNotNull(vc)
  }

  @Test
  fun `create throws if data cannot be parsed into a json object`() {
    val keyManager = InMemoryKeyManager()
    val issuerDid = DidKey.create(keyManager)
    val holderDid = DidKey.create(keyManager)

    val exception = assertThrows(IllegalArgumentException::class.java) {
      VerifiableCredential.create(
        type = "StreetCred",
        issuer = issuerDid.uri,
        subject = holderDid.uri,
        data = "trials & tribulations"
      )
    }

    // Optionally, further verify the exception (e.g., check the message)
    assertEquals("expected data to be parseable into a JSON object", exception.message)
  }

  @Test
  fun `signing works`() {
    val keyManager = InMemoryKeyManager()
    val issuerDid = DidKey.create(keyManager)
    val holderDid = DidKey.create(keyManager)

    val vc = VerifiableCredential.create(
      type = "StreetCred",
      issuer = issuerDid.uri,
      subject = holderDid.uri,
      data = StreetCredibility(localRespect = "high", legit = true)
    )

    val vcJwt = vc.sign(issuerDid)
    val parsedJwt = SignedJWT.parse(vcJwt) // validates JWT

    val didDocument = issuerDid.resolve().didDocument
    val assertionMethod = didDocument.assertionMethodVerificationMethodsDereferenced.first()

    val jwtHeader = parsedJwt.header
    assertNotNull(jwtHeader.algorithm)
    assertEquals(JOSEObjectType.JWT, jwtHeader.type)
    assertEquals(assertionMethod.id.toString(), jwtHeader.keyID)

    val jwtClaims = parsedJwt.jwtClaimsSet
    assertNotNull(jwtClaims.issueTime)
    assertNotNull(jwtClaims.getClaim("vc"))
    assertEquals(issuerDid.uri, jwtClaims.issuer)
    assertEquals(holderDid.uri, jwtClaims.subject)

    val vcDataModelJson = jwtClaims.getJSONObjectClaim("vc")
    val vcDataModel = VcDataModel.fromMap(vcDataModelJson)

    assertEquals(holderDid.uri, vcDataModel.credentialSubject.id.toString())
    assertContains(vcDataModel.types, "StreetCred")
  }

  @Test
  fun `verify does not throw an exception if vc is legit`() {
    val keyManager = InMemoryKeyManager()
    val issuerDid = DidKey.create(keyManager)
    val holderDid = DidKey.create(keyManager)

    val vc = VerifiableCredential.create(
      type = "StreetCred",
      issuer = issuerDid.uri,
      subject = holderDid.uri,
      data = StreetCredibility(localRespect = "high", legit = true)
    )

    val vcJwt = vc.sign(issuerDid)
    VerifiableCredential.verify(vcJwt)
  }

  @Test
  fun `verify handles DIDs without an assertionMethod`() {
    val keyManager = InMemoryKeyManager()

    //Create an ION DID without an assertionMethod
    val alias = keyManager.generatePrivateKey(JWSAlgorithm.ES256K)
    val verificationJwk = keyManager.getPublicKey(alias)
    val key = PublicKey(
      id = UUID.randomUUID().toString(),
      type = "JsonWebKey2020",
      publicKeyJwk = verificationJwk,
      purposes = emptyList() //No assertionMethod
    )
    val issuerDid = DidIonManager.create(
      InMemoryKeyManager(),
      CreateDidIonOptions(verificationPublicKey = key)
    )

    val header = JWSHeader.Builder(JWSAlgorithm.ES256K)
      .keyID(issuerDid.uri)
      .build()
    //A detached payload JWT
    val vcJwt = "${header.toBase64URL()}..fakeSig"

    val exception = assertThrows(SignatureException::class.java) {
      VerifiableCredential.verify(vcJwt)
    }
    assertEquals("Signature verification failed: Expected kid in JWS header to dereference a DID Document " +
      "Verification Method with an Assertion verification relationship", exception.message)
  }

  @Test
  fun `parseJwt throws ParseException if argument is not a valid JWT`() {
    assertThrows(ParseException::class.java) {
      VerifiableCredential.parseJwt("hi")
    }
  }

  @Test
  fun `parseJwt throws if vc property is missing in JWT`() {
    val jwk = OctetKeyPairGenerator(Curve.Ed25519).generate()
    val signer: JWSSigner = Ed25519Signer(jwk)

    val claimsSet = JWTClaimsSet.Builder()
      .subject("alice")
      .build()

    val signedJWT = SignedJWT(
      JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(jwk.keyID).build(),
      claimsSet
    )

    signedJWT.sign(signer)
    val randomJwt = signedJWT.serialize()
    val exception = assertThrows(IllegalArgumentException::class.java) {
      VerifiableCredential.parseJwt(randomJwt)
    }

    assertEquals("jwt payload missing vc property", exception.message)
  }

  @Test
  fun `parseJwt throws if vc property in JWT payload is not an object`() {
    val jwk = OctetKeyPairGenerator(Curve.Ed25519).generate()
    val signer: JWSSigner = Ed25519Signer(jwk)

    val claimsSet = JWTClaimsSet.Builder()
      .subject("alice")
      .claim("vc", "hehe troll")
      .build()

    val signedJWT = SignedJWT(
      JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(jwk.keyID).build(),
      claimsSet
    )

    signedJWT.sign(signer)
    val randomJwt = signedJWT.serialize()

    val exception = assertThrows(IllegalArgumentException::class.java) {
      VerifiableCredential.parseJwt(randomJwt)
    }

    assertEquals("expected vc property in JWT payload to be an object", exception.message)
  }

  @Test
  fun `parseJwt returns an instance of VerifiableCredential on success`() {
    val keyManager = InMemoryKeyManager()
    val issuerDid = DidKey.create(keyManager)
    val holderDid = DidKey.create(keyManager)

    val vc = VerifiableCredential.create(
      type = "StreetCred",
      issuer = issuerDid.uri,
      subject = holderDid.uri,
      data = StreetCredibility(localRespect = "high", legit = true)
    )

    val vcJwt = vc.sign(issuerDid)

    val parsedVc = VerifiableCredential.parseJwt(vcJwt)
    assertNotNull(parsedVc)

    assertEquals(vc.type, parsedVc.type)
    assertEquals(vc.issuer, parsedVc.issuer)
    assertEquals(vc.subject, parsedVc.subject)
  }
}