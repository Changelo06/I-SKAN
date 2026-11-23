import argparse
import ipaddress
import json
import socket
from datetime import datetime, timedelta, timezone
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID

BASE = Path(__file__).resolve().parent.parent


def setup(ip):
    address = ipaddress.ip_address(ip)
    if not address.is_private or address.is_loopback or address.is_unspecified:
        raise ValueError('Use the laptop private Wi-Fi IPv4/IPv6 address.')
    target = BASE / 'certs'
    target.mkdir(exist_ok=True)
    now = datetime.now(timezone.utc)
    key_path = target / 'root-key.pem'
    cert_path = target / 'root-ca.pem'
    if key_path.exists() and cert_path.exists():
        ca_key = serialization.load_pem_private_key(key_path.read_bytes(), password=None)
        ca = x509.load_pem_x509_certificate(cert_path.read_bytes())
        if ca.not_valid_after_utc < now + timedelta(days=400):
            raise ValueError('Root certificate nearly expired. Back up certs, regenerate, and trust the new root on both devices.')
    else:
        ca_key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'I-SKAN private laptop CA')])
        ca = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(ca_key.public_key())
              .serial_number(x509.random_serial_number()).not_valid_before(now - timedelta(minutes=5))
              .not_valid_after(now + timedelta(days=3650))
              .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)
              .add_extension(x509.KeyUsage(digital_signature=True, content_commitment=False, key_encipherment=False,
                             data_encipherment=False,key_agreement=False,key_cert_sign=True,crl_sign=True,
                             encipher_only=False,decipher_only=False), critical=True)
              .sign(ca_key, hashes.SHA256()))
        key_path.write_bytes(ca_key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        cert_path.write_bytes(ca.public_bytes(serialization.Encoding.PEM))
    server_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'I-SKAN laptop')])
    server = (x509.CertificateBuilder().subject_name(subject).issuer_name(ca.subject).public_key(server_key.public_key())
              .serial_number(x509.random_serial_number()).not_valid_before(now - timedelta(minutes=5))
              .not_valid_after(now + timedelta(days=397))
              .add_extension(x509.SubjectAlternativeName([x509.DNSName('localhost'),x509.IPAddress(address),
                              x509.IPAddress(ipaddress.ip_address('127.0.0.1')),x509.IPAddress(ipaddress.ip_address('::1'))]), critical=False)
              .add_extension(x509.BasicConstraints(ca=False,path_length=None), critical=True)
              .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]), critical=False)
              .sign(ca_key, hashes.SHA256()))
    (target / 'server-key.pem').write_bytes(server_key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    (target / 'server.pem').write_bytes(server.public_bytes(serialization.Encoding.PEM))
    (target / 'root-ca.cer').write_bytes(ca.public_bytes(serialization.Encoding.DER))
    (target / 'config.json').write_text(json.dumps({'ip':str(address)}))
    print('HTTPS_CERTIFICATES_READY')
    print('Install only certs/root-ca.cer on Windows and Android. Never share the private key files.')
    print('Phone certificate trust and camera permission must be checked on the actual device.')


if __name__ == '__main__':
    p=argparse.ArgumentParser();p.add_argument('--ip',required=True);args=p.parse_args()
    try:
        setup(args.ip)
    except Exception:
        print('HTTPS_SETUP_FAILED: verify the private Wi-Fi address, existing CA validity, and folder access.')
        raise SystemExit(1)
